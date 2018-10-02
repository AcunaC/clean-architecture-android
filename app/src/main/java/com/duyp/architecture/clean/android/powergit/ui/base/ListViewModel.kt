package com.duyp.architecture.clean.android.powergit.ui.base

import com.duyp.architecture.clean.android.powergit.Event
import com.duyp.architecture.clean.android.powergit.domain.entities.ListEntity
import com.duyp.architecture.clean.android.powergit.printStacktraceIfDebug
import com.duyp.architecture.clean.android.powergit.ui.features.repo.list.BasicListViewModel
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers

/**
 * ViewModel for [ListFragment] to show a collection in RecyclerView.
 *
 * Receives [ListIntent] from the view to perform data loading (refreshing or loading more page) and updates the view
 * by [ListState] which contains some basic states for every list.
 *
 * It also implements [AdapterData] that any Adapter can access and render data on RecyclerView. The idea is in
 * Adapter we don't have any reference to list of data, all should be controlled by ViewModel to be persisted against
 * screen rotating, and more important: TESTABLE.
 *
 * The data is wrapped in [ListEntity] for pagination (and lazy load in some cases), see below explanation:
 *
 * @param [EntityType] is type of data to be shown in adapter
 *
 * @param [ListType] is type of data in the list. In common case (see [BasicListViewModel]), [EntityType] is the same
 * with [ListType] when we store data in memory. But in some other cases, we only save id of items on memory and do
 * lazy load when the item is being bound in adapter, this case the [ListType] is [Int] (id). Therefore the child
 * classes must implement [getItem] which returns [EntityType] based on [ListType]
 *
 * See some examples:
 *  - Basic [com.duyp.architecture.clean.android.powergit.ui.features.repo.list.RepoListViewModel]
 *  - Extended [com.duyp.architecture.clean.android.powergit.ui.features.repo.list.RepoListViewModel]
 *
 * @param [S] View State
 * @param [I] View Intent, must extends [ListIntent]
 */
abstract class ListViewModel<S, I: ListIntent, EntityType, ListType>: BaseViewModel<S, I>(), AdapterData<EntityType> {

    private var mListEntity: ListEntity<ListType>? = null

    private var mIsLoading: Boolean = false

    override fun composeIntent(intentSubject: Observable<I>) {
        // force view to refresh
        withListState {
            // only refresh one time (no refresh if UI get rotated)
            if (refresh == null) {
                setListState { copy(refresh = Event.empty()) }
            }
        }

        // refresh intent
        addDisposable {
            intentSubject.ofType(getRefreshIntent()::class.java)
                    .subscribeOn(Schedulers.io())
                    // do nothing if loading is in progress
                    .filter { !mIsLoading }
                    .switchMap { loadPage(ListEntity.STARTING_PAGE) }
                    .subscribe()
        }

        // load more intent
        addDisposable {
            intentSubject.ofType(getLoadMoreIntent()::class.java)
                    .subscribeOn(Schedulers.io())
                    // do nothing if loading is in progress and can't load more with current list
                    .filter { !mIsLoading && (mListEntity?.canLoadMore() ?: false) }
                    .doOnNext {
                        setListState { copy(loadingMore = Event.empty()) }
                    }
                    .switchMap { loadPage(mListEntity!!.next) }
                    .subscribe()
        }
    }

    override fun getTotalCount(): Int = mListEntity?.items?.size ?: 0

    override fun getItemAtPosition(position: Int): EntityType? {
        if (mListEntity == null || mListEntity!!.items.isEmpty() || position < 0 || position >= getTotalCount())
            return null
        return getItem(mListEntity!!.items[position])
    }

    protected abstract fun getItem(listItem: ListType): EntityType

    /**
     * Set new list state based on current state.
     *
     * The final view model (implementer) might have the state is combination of [ListState] and other states such as
     * sorting... So it has to implement this function to set the list state properly
     */
    protected abstract fun setListState(s: ListState.() -> ListState)

    /**
     * Access current list state based on current state
     */
    protected abstract fun withListState(s: ListState.() -> Unit)

    /**
     * Implement this to load specific page
     */
    protected abstract fun loadPageObservable(page: Int): Observable<ListEntity<ListType>>

    /**
     * In some cases the final view model might have more intents than basic [ListIntent], so it has to specific
     * refresh intent as well as load more intent to make this class's functionality works
     */
    abstract fun getRefreshIntent(): I

    /**
     * see [getRefreshIntent]
     */
    abstract fun getLoadMoreIntent(): I

    /**
     * Load specific page and set corresponding state (show loading, load completed, offline notice if data is came
     * from offline storage...)
     */
    private fun loadPage(page: Int): Observable<ListEntity<ListType>> {
        return loadPageObservable(page)
                .doOnSubscribe {
                    mIsLoading = true
                    setListState { copy(showLoading = page == ListEntity.STARTING_PAGE) }
                }
                .doOnNext {
                    mListEntity = it
                    mIsLoading = false
                    setListState {
                        copy(
                                showLoading = false,
                                loadCompleted = Event.empty(),
                                showOfflineNotice = !mListEntity!!.isApiData
                        )
                    }
                }
                .doOnError {
                    it.printStacktraceIfDebug()
                    mIsLoading = false
                    setListState { copy(showLoading = false, errorMessage = Event(it.message ?: "")) }
                }
                .onErrorResumeNext { _: Throwable -> Observable.empty() }
    }
}

data class ListState(
        val showLoading: Boolean = false,
        val showOfflineNotice: Boolean = false,
        val refresh: Event<Unit>? = null,
        val errorMessage: Event<String>? = null,
        val loadingMore: Event<Unit>? = null,
        val loadCompleted: Event<Unit>? = null
)

interface ListIntent {
    object RefreshIntent: ListIntent
    object LoadMoreIntent: ListIntent
}