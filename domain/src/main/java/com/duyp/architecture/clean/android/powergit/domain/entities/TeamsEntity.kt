package com.duyp.architecture.clean.android.powergit.domain.entities

data class TeamsEntity(

        var id: Long = 0,

        var url: String? = null,

        var name: String? = null,

        var slug: String? = null,

        var description: String? = null,

        var privacy: String? = null,

        var permission: String? = null,

        var membersUrl: String? = null,

        var repositoriesUrl: String? = null
)