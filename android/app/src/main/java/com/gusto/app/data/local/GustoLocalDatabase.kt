package com.gusto.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.gson.Gson
import com.gusto.app.data.model.Recipe
import com.gusto.app.data.model.ShoppingItem

class GustoLocalDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    private val gson = Gson()

    companion object {
        private const val DATABASE_NAME = "gusto_local.db"
        private const val DATABASE_VERSION = 3

        private const val TABLE_RECIPES = "recipes"
        private const val COLUMN_ID = "id"
        private const val COLUMN_JSON = "json_data"
        private const val COLUMN_IS_FAVORITE = "is_favorite"
        private const val COLUMN_FAVORITED_AT = "favorited_at"
        private const val COLUMN_COOK_COUNT = "cook_count"
        private const val COLUMN_LAST_COOKED_AT = "last_cooked_at"
        private const val COLUMN_UPDATED_AT = "updated_at"

        private const val TABLE_FRIDGE = "fridge_items"
        private const val COLUMN_FRIDGE_NAME = "name"
        private const val COLUMN_FRIDGE_CREATED_AT = "created_at"

        private const val TABLE_SHOPPING = "shopping_list_items"
        private const val COLUMN_SHOPPING_ID = "id"
        private const val COLUMN_SHOPPING_NAME = "name"
        private const val COLUMN_SHOPPING_QUANTITY = "quantity"
        private const val COLUMN_SHOPPING_UNIT = "unit"
        private const val COLUMN_SHOPPING_IS_CHECKED = "is_checked"
        private const val COLUMN_SHOPPING_SOURCE = "source_recipe_title"
        private const val COLUMN_SHOPPING_CREATED_AT = "created_at"

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
                $COLUMN_FAVORITED_AT INTEGER DEFAULT 0,
                $COLUMN_COOK_COUNT INTEGER DEFAULT 0,
                $COLUMN_LAST_COOKED_AT INTEGER DEFAULT 0,
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

        val createShoppingQuery = """
            CREATE TABLE IF NOT EXISTS $TABLE_SHOPPING (
                $COLUMN_SHOPPING_ID TEXT PRIMARY KEY,
                $COLUMN_SHOPPING_NAME TEXT NOT NULL,
                $COLUMN_SHOPPING_QUANTITY REAL,
                $COLUMN_SHOPPING_UNIT TEXT,
                $COLUMN_SHOPPING_IS_CHECKED INTEGER DEFAULT 0,
                $COLUMN_SHOPPING_SOURCE TEXT,
                $COLUMN_SHOPPING_CREATED_AT INTEGER NOT NULL
            )
        """.trimIndent()
        db.execSQL(createShoppingQuery)
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
        if (oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE $TABLE_RECIPES ADD COLUMN $COLUMN_FAVORITED_AT INTEGER DEFAULT 0")
            } catch (_: Exception) {}
            try {
                db.execSQL("ALTER TABLE $TABLE_RECIPES ADD COLUMN $COLUMN_COOK_COUNT INTEGER DEFAULT 0")
            } catch (_: Exception) {}
            try {
                db.execSQL("ALTER TABLE $TABLE_RECIPES ADD COLUMN $COLUMN_LAST_COOKED_AT INTEGER DEFAULT 0")
            } catch (_: Exception) {}

            val createShoppingQuery = """
                CREATE TABLE IF NOT EXISTS $TABLE_SHOPPING (
                    $COLUMN_SHOPPING_ID TEXT PRIMARY KEY,
                    $COLUMN_SHOPPING_NAME TEXT NOT NULL,
                    $COLUMN_SHOPPING_QUANTITY REAL,
                    $COLUMN_SHOPPING_UNIT TEXT,
                    $COLUMN_SHOPPING_IS_CHECKED INTEGER DEFAULT 0,
                    $COLUMN_SHOPPING_SOURCE TEXT,
                    $COLUMN_SHOPPING_CREATED_AT INTEGER NOT NULL
                )
            """.trimIndent()
            db.execSQL(createShoppingQuery)
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
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_IS_FAVORITE, if (newFav) 1 else 0)
            put(COLUMN_FAVORITED_AT, if (newFav) System.currentTimeMillis() else 0L)
        }
        db.update(TABLE_RECIPES, values, "$COLUMN_ID = ?", arrayOf(recipe.id))
        return newFav
    }

    fun incrementCookCount(recipeId: String) {
        val db = writableDatabase
        val query = """
            UPDATE $TABLE_RECIPES 
            SET $COLUMN_COOK_COUNT = $COLUMN_COOK_COUNT + 1,
                $COLUMN_LAST_COOKED_AT = ${System.currentTimeMillis()}
            WHERE $COLUMN_ID = ?
        """.trimIndent()
        db.execSQL(query, arrayOf(recipeId))
    }

    fun getFavoriteRecipesByOldest(): List<Recipe> {
        val list = mutableListOf<Recipe>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_RECIPES,
            arrayOf(COLUMN_JSON),
            "$COLUMN_IS_FAVORITE = 1",
            null,
            null,
            null,
            "CASE WHEN $COLUMN_FAVORITED_AT > 0 THEN $COLUMN_FAVORITED_AT ELSE $COLUMN_UPDATED_AT END ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                try {
                    val recipe = gson.fromJson(it.getString(0), Recipe::class.java)
                    if (recipe != null) list.add(recipe)
                } catch (_: Exception) {}
            }
        }
        return list
    }

    fun getMostCookedRecipes(limit: Int = 10): List<Recipe> {
        val list = mutableListOf<Recipe>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_RECIPES,
            arrayOf(COLUMN_JSON),
            "$COLUMN_COOK_COUNT > 0",
            null,
            null,
            null,
            "$COLUMN_COOK_COUNT DESC, $COLUMN_LAST_COOKED_AT DESC",
            limit.toString()
        )
        cursor.use {
            while (it.moveToNext()) {
                try {
                    val recipe = gson.fromJson(it.getString(0), Recipe::class.java)
                    if (recipe != null) list.add(recipe)
                } catch (_: Exception) {}
            }
        }
        return list
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

    // ==========================================
    // PERSISTÊNCIA EM BANCO: LISTA DE COMPRAS
    // ==========================================

    fun addShoppingItem(item: ShoppingItem): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_SHOPPING_ID, item.id)
            put(COLUMN_SHOPPING_NAME, item.name.trim())
            put(COLUMN_SHOPPING_QUANTITY, item.quantity)
            put(COLUMN_SHOPPING_UNIT, item.unit)
            put(COLUMN_SHOPPING_IS_CHECKED, if (item.isChecked) 1 else 0)
            put(COLUMN_SHOPPING_SOURCE, item.sourceRecipeTitle)
            put(COLUMN_SHOPPING_CREATED_AT, item.createdAt)
        }
        val res = db.insertWithOnConflict(TABLE_SHOPPING, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        return res != -1L
    }

    fun addShoppingItems(items: List<ShoppingItem>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (item in items) {
                val values = ContentValues().apply {
                    put(COLUMN_SHOPPING_ID, item.id)
                    put(COLUMN_SHOPPING_NAME, item.name.trim())
                    put(COLUMN_SHOPPING_QUANTITY, item.quantity)
                    put(COLUMN_SHOPPING_UNIT, item.unit)
                    put(COLUMN_SHOPPING_IS_CHECKED, if (item.isChecked) 1 else 0)
                    put(COLUMN_SHOPPING_SOURCE, item.sourceRecipeTitle)
                    put(COLUMN_SHOPPING_CREATED_AT, item.createdAt)
                }
                db.insertWithOnConflict(TABLE_SHOPPING, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun toggleShoppingItem(id: String, isChecked: Boolean): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_SHOPPING_IS_CHECKED, if (isChecked) 1 else 0)
        }
        val rows = db.update(TABLE_SHOPPING, values, "$COLUMN_SHOPPING_ID = ?", arrayOf(id))
        return rows > 0
    }

    fun removeShoppingItem(id: String): Boolean {
        val db = writableDatabase
        val rows = db.delete(TABLE_SHOPPING, "$COLUMN_SHOPPING_ID = ?", arrayOf(id))
        return rows > 0
    }

    fun getShoppingItems(): List<ShoppingItem> {
        val list = mutableListOf<ShoppingItem>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_SHOPPING,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_SHOPPING_IS_CHECKED ASC, $COLUMN_SHOPPING_CREATED_AT DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getString(it.getColumnIndexOrThrow(COLUMN_SHOPPING_ID))
                val name = it.getString(it.getColumnIndexOrThrow(COLUMN_SHOPPING_NAME))
                val quantity = if (it.isNull(it.getColumnIndexOrThrow(COLUMN_SHOPPING_QUANTITY))) null else it.getDouble(it.getColumnIndexOrThrow(COLUMN_SHOPPING_QUANTITY))
                val unit = it.getString(it.getColumnIndexOrThrow(COLUMN_SHOPPING_UNIT))
                val isChecked = it.getInt(it.getColumnIndexOrThrow(COLUMN_SHOPPING_IS_CHECKED)) == 1
                val source = it.getString(it.getColumnIndexOrThrow(COLUMN_SHOPPING_SOURCE))
                val createdAt = it.getLong(it.getColumnIndexOrThrow(COLUMN_SHOPPING_CREATED_AT))

                list.add(
                    ShoppingItem(
                        id = id,
                        name = name,
                        quantity = quantity,
                        unit = unit,
                        isChecked = isChecked,
                        sourceRecipeTitle = source,
                        createdAt = createdAt
                    )
                )
            }
        }
        return list
    }

    fun clearCompletedShoppingItems(): Boolean {
        val db = writableDatabase
        val rows = db.delete(TABLE_SHOPPING, "$COLUMN_SHOPPING_IS_CHECKED = 1", null)
        return rows >= 0
    }

    fun clearAllShoppingItems(): Boolean {
        val db = writableDatabase
        val rows = db.delete(TABLE_SHOPPING, null, null)
        return rows >= 0
    }

    /**
     * Transfere todos os itens marcados como comprados diretamente para a geladeira (TABLE_FRIDGE)
     * e os remove da lista de compras.
     */
    fun transferCheckedItemsToFridge(): List<String> {
        val transferred = mutableListOf<String>()
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cursor = db.query(
                TABLE_SHOPPING,
                arrayOf(COLUMN_SHOPPING_NAME),
                "$COLUMN_SHOPPING_IS_CHECKED = 1",
                null,
                null,
                null,
                null
            )
            cursor.use {
                while (it.moveToNext()) {
                    val name = it.getString(0)
                    if (name.isNotBlank()) {
                        transferred.add(name)
                        addFridgeItem(name)
                    }
                }
            }
            // Remove os itens transferidos da lista de compras
            db.delete(TABLE_SHOPPING, "$COLUMN_SHOPPING_IS_CHECKED = 1", null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return transferred
    }
}


