package me.allenzjl.domaincache;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * The type Cache storage helper.
 */
public class CacheStorageHelper extends SQLiteOpenHelper {

    public static final int DB_VERSION = 2;

    public static final String DB_NAME = "DOMAIN_CACHE.db";

    public static final String TABLE_CACHE = "CACHE";

    public static final String TABLE_ALIAS = "ALIAS";

    public static final String TABLE_PARAMS = "PARAMS";

    public static final int PARAMS_MAX_COLUMN = 20;


    public CacheStorageHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createCacheTable(db);
        createAliasTable(db);
        createParamsTable(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new UnsupportedOperationException("Reinstall the app");
    }

    protected void createCacheTable(SQLiteDatabase db) {
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("CREATE TABLE IF NOT EXISTS ").append(TABLE_CACHE).append(" (");
        sqlBuilder.append(CacheTableColumn.ID).append(" INTEGER PRIMARY KEY AUTOINCREMENT, ");
        sqlBuilder.append(CacheTableColumn.KEY).append(" TEXT, ");
        sqlBuilder.append(CacheTableColumn.PARAMETER).append(" TEXT, ");
        sqlBuilder.append(CacheTableColumn.RESULT).append(" TEXT, ");
        sqlBuilder.append(CacheTableColumn.ALIAS).append(" TEXT, ");
        sqlBuilder.append(CacheTableColumn.EXPIRED).append(" TEXT);");
        db.execSQL(sqlBuilder.toString());
    }

    protected void createAliasTable(SQLiteDatabase db) {
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("CREATE TABLE IF NOT EXISTS ").append(TABLE_ALIAS).append(" (");
        sqlBuilder.append(AliasTableColumn.ID).append(" INTEGER PRIMARY KEY AUTOINCREMENT, ");
        sqlBuilder.append(AliasTableColumn.ALIAS).append(" TEXT, ");
        sqlBuilder.append(AliasTableColumn.KEY).append(" TEXT, ");
        sqlBuilder.append(AliasTableColumn.PARAM_NAMES).append(" TEXT);");
        db.execSQL(sqlBuilder.toString());
    }

    protected void createParamsTable(SQLiteDatabase db) {
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("CREATE TABLE IF NOT EXISTS ").append(TABLE_PARAMS).append(" (");
        sqlBuilder.append(ParamsTableColumn.CACHE_ID).append(" INTEGER PRIMARY KEY, ");
        sqlBuilder.append(ParamsTableColumn.ALIAS_ID).append(" INTEGER, ");
        for (int i = 0; i < PARAMS_MAX_COLUMN; i++) {
            sqlBuilder.append(ParamsTableColumn.PARAM).append(i).append(" TEXT");
            if (i < PARAMS_MAX_COLUMN - 1) {
                sqlBuilder.append(", ");
            } else {
                sqlBuilder.append(");");
            }
        }
        db.execSQL(sqlBuilder.toString());
    }

    public static class CacheTableColumn {
        public static final String ID = "_id";

        public static final String KEY = "_key";

        public static final String PARAMETER = "_parameter";

        public static final String RESULT = "_result";

        public static final String ALIAS = "_alias";

        public static final String EXPIRED = "_expired";
    }

    public static class AliasTableColumn {
        public static final String ID = "_id";

        public static final String ALIAS = "_alias";

        public static final String KEY = "_key";

        public static final String PARAM_NAMES = "_param_names";
    }

    public static class ParamsTableColumn {
        public static final String CACHE_ID = "_cache_id";

        public static final String ALIAS_ID = "_alias_id";

        public static final String PARAM = "_param";
    }
}
