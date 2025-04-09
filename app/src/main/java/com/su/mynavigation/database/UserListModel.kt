package com.su.mynavigation.database

data class UserListModel(
    val name: String,
    val about: String,
    val steps: Int,
    val goal: Int,
    val progress: Int,
    val rewards: Int,
    val milestone: Int
)