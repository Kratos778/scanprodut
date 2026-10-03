package com.elizier.stockscan.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    @Query("SELECT * FROM catalog ORDER BY category, name")
    fun getAll(): Flow<List<CatalogItem>>

    @Query("SELECT * FROM catalog WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CatalogItem?

    @Insert
    suspend fun insert(item: CatalogItem): Long

    @Update
    suspend fun update(item: CatalogItem)

    @Delete
    suspend fun delete(item: CatalogItem)

    @Query("DELETE FROM catalog")
    suspend fun deleteAll()
}

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

    /** Actualiza nome/categoria/preço/custo em TODAS as unidades desse catálogo (ainda available) */
    @Query("""
        UPDATE products SET name = :name, category = :category, price = :price, cost = :cost
        WHERE catalogId = :catalogId AND status = 'available'
        """)
    suspend fun syncFromCatalog(catalogId: Long, name: String, category: String, price: Int, cost: Int): Int

    @Query("UPDATE products SET status = 'sold', soldAt = :soldAt, soldTo = :customer WHERE code = :code AND status = 'available'")
    suspend fun markSold(code: String, soldAt: Long, customer: String): Int

    @Delete
    suspend fun delete(product: Product)

    /** Apaga unidades (available) ligadas ao catálogo */
    @Query("DELETE FROM products WHERE catalogId = :catalogId AND status = 'available'")
    suspend fun deleteAvailableByCatalog(catalogId: Long): Int

    @Query("DELETE FROM products")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM products WHERE status = 'available'")
    fun countAvailable(): Flow<Int>

    @Query("SELECT COUNT(*) FROM products WHERE status = 'available' AND catalogId = :catalogId")
    suspend fun countAvailableByCatalog(catalogId: Long): Int

    @Query("SELECT COUNT(*) FROM products WHERE catalogId = :catalogId")
    suspend fun countAllByCatalog(catalogId: Long): Int
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY timestamp DESC LIMIT 150")
    fun getRecent(): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history WHERE type = 'out' AND timestamp >= :fromTs ORDER BY timestamp DESC")
    suspend fun salesSince(fromTs: Long): List<HistoryEntry>

    @Insert
    suspend fun insert(entry: HistoryEntry): Long

    @Query("SELECT * FROM history WHERE type = 'out' ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastSale(): HistoryEntry?

    @Delete
    suspend fun delete(entry: HistoryEntry)

    @Query("DELETE FROM history")
    suspend fun deleteAll()
}

@Database(
    entities = [CatalogItem::class, Product::class, HistoryEntry::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
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
