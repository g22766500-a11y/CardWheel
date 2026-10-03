package com.example.cardwheel

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface CardDao {

    @Query(
        "SELECT * FROM cards ORDER BY id DESC"
    )
    fun getAll(): List<CardItem>

    @Query(
        "SELECT * FROM cards WHERE id = :id LIMIT 1"
    )
    fun getById(
        id: Int
    ): CardItem?

    @Insert
    fun insert(
        card: CardItem
    )

    @Update
    fun update(card: CardItem)

    @Delete
    fun delete(
        card: CardItem
    )
}