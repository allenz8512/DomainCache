package me.allenzjl.domaincache;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * The type Cache storage helper.
 */
public class CacheStorageHelper extends SQLiteOpenHelper {

    public static final int DB_VERSION = 1;

    public static final String DB_NAME = "DOMAIN_CACHE.db";

    public static final String TABLE_CACHE = "CACHE";

    private static final String SQL_CREATE_CACHE_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE_CACHE + " (" +
            Column.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            Column.KEY + " TEXT, " +
            Column.PARAMETER + " TEXT, " +
            Column.RESULT + " TEXT, " +
            Column.ALIAS + " TEXT, " +
            Column.EXPIRED + " TEXT);";

    public CacheStorageHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_CACHE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public static class Column {
        public static final String ID = "_id";

        public static final String KEY = "_key";

        public static final String PARAMETER = "_parameter";

        public static final String RESULT = "_result";

        public static final String ALIAS = "_alias";

        public static final String EXPIRED = "_expired";
    }
}
