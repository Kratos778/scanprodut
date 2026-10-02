package com.elizier.stockscan.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY category, name")
    fun getAll(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE status = 'available' ORDER BY category, name")
    fun getAvailable(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE status = 'sold' ORDER BY soldAt DESC")
    fun getSold(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE code = :code LIMIT 1")
    suspend fun getByCode(code: String): Product?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(product: Product)

    @Update
    suspend fun update(product: Product)

    @Query("UPDATE products SET status = 'sold', soldAt = :soldAt, soldTo = :customer WHERE code = :code AND status = 'available'")
    suspend fun markSold(code: String, soldAt: Long, customer: String): Int

    @Delete
    suspend fun delete(product: Product)

    @Query("DELETE FROM products")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM products WHERE status = 'available'")
    fun countAvailable(): Flow<Int>

    @Query("SELECT COUNT(*) FROM products WHERE status = 'available' AND category = :cat")
    suspend fun countAvailableInCategory(cat: String): Int

    @Query("SELECT DISTINCT category FROM products ORDER BY category")
    fun getCategories(): Flow<List<String>>
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY timestamp DESC LIMIT 150")
    fun getRecent(): Flow<List<HistoryEntry>>

    @Insert
    suspend fun insert(entry: HistoryEntry): Long

    @Query("SELECT * FROM history WHERE type = 'out' ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastSale(): HistoryEntry?

    @Delete
    suspend fun delete(entry: HistoryEntry)

    @Query("DELETE FROM history")
    suspend fun deleteAll()
}

@Database(entities = [Product::class, HistoryEntry::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "stockscan.db"
                )
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }
        }
    }
}
