package com.gusto.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.gson.Gson
import com.gusto.app.data.model.Recipe

class GustoLocalDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    private val gson = Gson()

    companion object {
        private const val DATABASE_NAME = "gusto_local.db"
        private const val DATABASE_VERSION = 2

        private const val TABLE_RECIPES = "recipes"
        private const val COLUMN_ID = "id"
        private const val COLUMN_JSON = "json_data"
        private const val COLUMN_IS_FAVORITE = "is_favorite"
        private const val COLUMN_UPDATED_AT = "updated_at"

        private const val TABLE_FRIDGE = "fridge_items"
        private const val COLUMN_FRIDGE_NAME = "name"
        private const val COLUMN_FRIDGE_CREATED_AT = "created_at"

        @Volatile
        private var instance: GustoLocalDatabase? = null

        fun getInstance(context: Context): GustoLocalDatabase {
            return instance ?: synchronized(this) {
                instance ?: GustoLocalDatabase(context).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_RECIPES (
                $COLUMN_ID TEXT PRIMARY KEY,
                $COLUMN_JSON TEXT NOT NULL,
                $COLUMN_IS_FAVORITE INTEGER DEFAULT 0,
                $COLUMN_UPDATED_AT INTEGER NOT NULL
            )
        """.trimIndent()
        db.execSQL(createTableQuery)
        db.execSQL("CREATE INDEX idx_fav ON $TABLE_RECIPES ($COLUMN_IS_FAVORITE)")

        val createFridgeQuery = """
            CREATE TABLE IF NOT EXISTS $TABLE_FRIDGE (
                $COLUMN_FRIDGE_NAME TEXT PRIMARY KEY,
                $COLUMN_FRIDGE_CREATED_AT INTEGER NOT NULL
            )
        """.trimIndent()
        db.execSQL(createFridgeQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            val createFridgeQuery = """
                CREATE TABLE IF NOT EXISTS $TABLE_FRIDGE (
                    $COLUMN_FRIDGE_NAME TEXT PRIMARY KEY,
                    $COLUMN_FRIDGE_CREATED_AT INTEGER NOT NULL
                )
            """.trimIndent()
            db.execSQL(createFridgeQuery)
        }
    }

    fun saveRecipe(recipe: Recipe, isFav: Boolean? = null) {
        val db = writableDatabase
        val existingFav = if (isFav != null) isFav else isFavorite(recipe.id)
        val values = ContentValues().apply {
            put(COLUMN_ID, recipe.id)
            put(COLUMN_JSON, gson.toJson(recipe))
            put(COLUMN_IS_FAVORITE, if (existingFav) 1 else 0)
            put(COLUMN_UPDATED_AT, System.currentTimeMillis())
        }
        db.insertWithOnConflict(TABLE_RECIPES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun saveRecipes(recipes: List<Recipe>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (recipe in recipes) {
                val existingFav = isFavorite(recipe.id)
                val values = ContentValues().apply {
                    put(COLUMN_ID, recipe.id)
                    put(COLUMN_JSON, gson.toJson(recipe))
                    put(COLUMN_IS_FAVORITE, if (existingFav) 1 else 0)
                    put(COLUMN_UPDATED_AT, System.currentTimeMillis())
                }
                db.insertWithOnConflict(TABLE_RECIPES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getRecipe(id: String): Recipe? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_RECIPES,
            arrayOf(COLUMN_JSON),
            "$COLUMN_ID = ?",
            arrayOf(id),
            null,
            null,
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                val json = it.getString(0)
                return try {
                    gson.fromJson(json, Recipe::class.java)
                } catch (e: Exception) {
                    null
                }
            }
        }
        return null
    }

    fun getAllCachedRecipes(): List<Recipe> {
        val list = mutableListOf<Recipe>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_RECIPES,
            arrayOf(COLUMN_JSON),
            null,
            null,
            null,
            null,
            "$COLUMN_UPDATED_AT DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                try {
                    val recipe = gson.fromJson(it.getString(0), Recipe::class.java)
                    if (recipe != null) list.add(recipe)
                } catch (e: Exception) {
                    // Ignora item corrompido
                }
            }
        }
        return list
    }

    fun getFavoriteRecipes(): List<Recipe> {
        val list = mutableListOf<Recipe>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_RECIPES,
            arrayOf(COLUMN_JSON),
            "$COLUMN_IS_FAVORITE = 1",
            null,
            null,
            null,
            "$COLUMN_UPDATED_AT DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                try {
                    val recipe = gson.fromJson(it.getString(0), Recipe::class.java)
                    if (recipe != null) list.add(recipe)
                } catch (e: Exception) {
                    // Ignora item corrompido
                }
            }
        }
        return list
    }

    fun isFavorite(id: String): Boolean {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_RECIPES,
            arrayOf(COLUMN_IS_FAVORITE),
            "$COLUMN_ID = ?",
            arrayOf(id),
            null,
            null,
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                return it.getInt(0) == 1
            }
        }
        return false
    }

    fun toggleFavorite(recipe: Recipe): Boolean {
        val currentFav = isFavorite(recipe.id)
        val newFav = !currentFav
        saveRecipe(recipe, newFav)
        return newFav
    }

    fun getFavoriteIds(): Set<String> {
        val set = mutableSetOf<String>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_RECIPES,
            arrayOf(COLUMN_ID),
            "$COLUMN_IS_FAVORITE = 1",
            null,
            null,
            null,
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                set.add(it.getString(0))
            }
        }
        return set
    }

    // ==========================================
    // PERSISTÊNCIA EM BANCO: MINHA GELADEIRA
    // ==========================================

    fun addFridgeItem(name: String): Boolean {
        val clean = name.trim()
        if (clean.isBlank()) return false
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_FRIDGE_NAME, clean)
            put(COLUMN_FRIDGE_CREATED_AT, System.currentTimeMillis())
        }
        val result = db.insertWithOnConflict(TABLE_FRIDGE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        return result != -1L
    }

    fun removeFridgeItem(name: String): Boolean {
        val clean = name.trim()
        if (clean.isBlank()) return false
        val db = writableDatabase
        val rows = db.delete(TABLE_FRIDGE, "LOWER($COLUMN_FRIDGE_NAME) = LOWER(?)", arrayOf(clean))
        return rows > 0
    }

    fun getFridgeItems(): List<String> {
        val list = mutableListOf<String>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_FRIDGE,
            arrayOf(COLUMN_FRIDGE_NAME),
            null,
            null,
            null,
            null,
            "$COLUMN_FRIDGE_CREATED_AT DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(it.getString(0))
            }
        }
        return list
    }

    fun clearFridgeItems(): Boolean {
        val db = writableDatabase
        val rows = db.delete(TABLE_FRIDGE, null, null)
        return rows >= 0
    }

    fun saveFridgeItems(items: List<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_FRIDGE, null, null)
            for (item in items) {
                val clean = item.trim()
                if (clean.isNotBlank()) {
                    val values = ContentValues().apply {
                        put(COLUMN_FRIDGE_NAME, clean)
                        put(COLUMN_FRIDGE_CREATED_AT, System.currentTimeMillis())
                    }
                    db.insertWithOnConflict(TABLE_FRIDGE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}

