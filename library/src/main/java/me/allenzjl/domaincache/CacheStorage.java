package me.allenzjl.domaincache;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import com.alibaba.fastjson.JSON;

import java.lang.reflect.Array;
import java.util.Date;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 缓存仓库。
 */
public class CacheStorage {

    private static class LazyHolder {
        private static CacheStorage INSTANCE = new CacheStorage();
    }

    public static CacheStorage getInstance() {
        return LazyHolder.INSTANCE;
    }

    public static final String CACHE_QUERY_WHERE_CLAUSE = CacheStorageHelper.CacheTableColumn.KEY +
            " = ? AND " + CacheStorageHelper.CacheTableColumn.PARAMETER + " = ?";

    public static final String CACHE_QUERY_SQL =
            "SELECT * FROM " + CacheStorageHelper.TABLE_CACHE + " WHERE " + CACHE_QUERY_WHERE_CLAUSE;

    public static final String ALIAS_QUERY_SQL = "SELECT * FROM " + CacheStorageHelper.TABLE_ALIAS + " WHERE " +
            CacheStorageHelper.AliasTableColumn.ALIAS + " = ? AND " + CacheStorageHelper.AliasTableColumn.KEY + " = ?";

    public static final String PARAMS_QUERY_WHERE_CLAUSE = CacheStorageHelper.ParamsTableColumn.CACHE_ID + " = ?";

    public static final String PARAMS_QUERY_SQL = "SELECT * FROM " + CacheStorageHelper.TABLE_PARAMS + " WHERE " +
            PARAMS_QUERY_WHERE_CLAUSE;

    public static final String ALIAS_QUERY_WHERE_CLAUSE_2 = CacheStorageHelper.AliasTableColumn.ALIAS + " = ?";

    public static final String ALIAS_QUERY_SQL_2 =
            "SELECT * FROM " + CacheStorageHelper.TABLE_ALIAS + " WHERE " + ALIAS_QUERY_WHERE_CLAUSE_2;

    public static final String CACHE_REMOVE_WHERE_CLAUSE = CacheStorageHelper.CacheTableColumn.ALIAS + " = ?";

    protected Context mContext;

    protected CacheStorageHelper mStorageHelper;

    protected LinkedBlockingQueue mOperationQueue;

    protected CacheStorage() {
        mContext = ContextAccessor.getApplicationContext();
        mStorageHelper = new CacheStorageHelper(mContext);
        mOperationQueue = new LinkedBlockingQueue();
    }

    @SuppressWarnings("unchecked")
    public <T> T getObject(String key, Object parameter, Class<T> resultClass) {
        return (T) get(key, parameter, resultClass, false);
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> getList(String key, Object parameter, Class<T> resultClass) {
        return (List<T>) get(key, parameter, resultClass, true);
    }

    @SuppressWarnings("unchecked")
    public <T> T[] getArray(String key, Object parameter, Class<T> resultClass) {
        List<T> list = (List<T>) get(key, parameter, resultClass, true);
        if (list != null) {
            T[] array = (T[]) Array.newInstance(resultClass, list.size());
            return list.toArray(array);
        } else {
            return null;
        }
    }

    protected Object get(String key, Object parameter, Class resultClass, boolean isArray) {
        synchronized (this) {
            String parameterJson = parameter == null ? "" : JSON.toJSONString(parameter);
            Cursor cursor = null;
            try {
                cursor = mStorageHelper.getReadableDatabase().rawQuery(CACHE_QUERY_SQL, new String[]{key, parameterJson});
                int count = cursor.getCount();
                if (count == 0) {
                    return null;
                } else if (count > 1) {
                    throw new IllegalStateException("Key '" + key + "' has more than one cache");
                } else {
                    cursor.moveToFirst();
                    long expired = cursor.getLong(cursor.getColumnIndex(CacheStorageHelper.CacheTableColumn.EXPIRED));
                    if (expired != 0) {
                        Date now = new Date();
                        if (now.after(new Date(expired))) {
                            return null;
                        }
                    }
                    String resultJson = cursor.getString(cursor.getColumnIndex(CacheStorageHelper.CacheTableColumn.RESULT));
                    if (isArray) {
                        return JSON.parseArray(resultJson, resultClass);
                    } else {
                        return JSON.parseObject(resultJson, resultClass);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
                mStorageHelper.close();
            }
        }
    }

    public void put(String key, Object parameter, Object result, int expire, String alias, String paramNames, Object... params) {
        synchronized (this) {
            SQLiteDatabase db = mStorageHelper.getWritableDatabase();
            db.beginTransaction();
            try {
                long cacheId = storeCache(db, key, parameter, result, alias, expire);
                if (!TextUtils.isEmpty(alias)) {
                    long aliasId = storeAlias(db, alias, key, paramNames);
                    storeParams(db, cacheId, aliasId, params);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            mStorageHelper.close();
        }
    }

    protected long storeCache(SQLiteDatabase db, String key, Object parameter, Object result, String alias, int expire) {
        Cursor cursor = null;
        try {
            String parameterJson = parameter == null ? "" : JSON.toJSONString(parameter);
            cursor = db.rawQuery(CACHE_QUERY_SQL, new String[]{key, parameterJson});
            int count = cursor.getCount();
            if (count > 1) {
                throw new IllegalStateException("Key '" + key + "' has more than one cache");
            }
            String resultJson = JSON.toJSONString(result);

            long expired;
            if (expire == 0) {
                expired = 0;
            } else {
                Date now = new Date();
                expired = now.getTime() + expire * 1000;
            }

            ContentValues values = new ContentValues();
            values.put(CacheStorageHelper.CacheTableColumn.RESULT, resultJson);
            values.put(CacheStorageHelper.CacheTableColumn.EXPIRED, expired);
            if (count == 0) {
                values.put(CacheStorageHelper.CacheTableColumn.KEY, key);
                values.put(CacheStorageHelper.CacheTableColumn.PARAMETER, parameterJson);
                values.put(CacheStorageHelper.CacheTableColumn.ALIAS, alias);
                return db.insert(CacheStorageHelper.TABLE_CACHE, null, values);
            } else {
                cursor.moveToFirst();
                long cacheId = cursor.getLong(cursor.getColumnIndex(CacheStorageHelper.CacheTableColumn.ID));
                db.update(CacheStorageHelper.TABLE_CACHE, values, CACHE_QUERY_WHERE_CLAUSE, new String[]{key, parameterJson});
                return cacheId;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    protected long storeAlias(SQLiteDatabase db, String alias, String key, String paramNames) {
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(ALIAS_QUERY_SQL, new String[]{alias, key});
            int count = cursor.getCount();
            if (count > 1) {
                throw new IllegalStateException("alias duplicated");
            } else if (count == 1) {
                cursor.moveToFirst();
                return cursor.getLong(cursor.getColumnIndex(CacheStorageHelper.AliasTableColumn.ID));
            } else {
                ContentValues values = new ContentValues();
                values.put(CacheStorageHelper.AliasTableColumn.ALIAS, alias);
                values.put(CacheStorageHelper.AliasTableColumn.KEY, key);
                values.put(CacheStorageHelper.AliasTableColumn.PARAM_NAMES, paramNames);
                return db.insert(CacheStorageHelper.TABLE_ALIAS, null, values);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    protected void storeParams(SQLiteDatabase db, long cacheId, long aliasId, Object... params) {
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(PARAMS_QUERY_SQL, new String[]{String.valueOf(cacheId)});
            int count = cursor.getCount();
            if (count > 1) {
                throw new IllegalStateException("params duplicated");
            }
            if (params.length > 0) {
                if (params.length > CacheStorageHelper.PARAMS_MAX_COLUMN) {
                    throw new IllegalArgumentException(
                            "Size of 'params' should not greater than " + CacheStorageHelper.PARAMS_MAX_COLUMN);
                }
                ContentValues paramsValues = new ContentValues();
                for (int i = 0; i < params.length; i++) {
                    String text = String.valueOf(params[i]);
                    paramsValues.put(CacheStorageHelper.ParamsTableColumn.PARAM + i, text);
                }
                if (count == 0) {
                    paramsValues.put(CacheStorageHelper.ParamsTableColumn.CACHE_ID, cacheId);
                    paramsValues.put(CacheStorageHelper.ParamsTableColumn.ALIAS_ID, aliasId);
                    db.insert(CacheStorageHelper.TABLE_PARAMS, null, paramsValues);
                } else {
                    db.update(CacheStorageHelper.TABLE_PARAMS, paramsValues, PARAMS_QUERY_WHERE_CLAUSE,
                            new String[]{String.valueOf(cacheId)});
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public void remove(String alias) {
        synchronized (this) {
            if (TextUtils.isEmpty(alias)) {
                return;
            }
            SQLiteDatabase db = mStorageHelper.getWritableDatabase();
            db.delete(CacheStorageHelper.TABLE_CACHE, CACHE_REMOVE_WHERE_CLAUSE, new String[]{alias});
            mStorageHelper.close();
        }
    }

    public void remove(String alias, String paramName, String compare, Object value) {
        synchronized (this) {
            SQLiteDatabase db = mStorageHelper.getWritableDatabase();
            db.beginTransaction();
            Cursor cursor = null;
            try {
                cursor = db.rawQuery(ALIAS_QUERY_SQL_2, new String[]{alias});
                int count = cursor.getCount();
                if (count == 0) {
                    return;
                }
                cursor.moveToFirst();
                while (!cursor.isAfterLast()) {
                    long aliasId = cursor.getLong(cursor.getColumnIndex(CacheStorageHelper.AliasTableColumn.ID));
                    String paramNames = cursor.getString(cursor.getColumnIndex(CacheStorageHelper.AliasTableColumn.PARAM_NAMES));
                    if (!TextUtils.isEmpty(paramNames)) {
                        String[] names = paramNames.split(",");
                        for (int i = 0; i < names.length; i++) {
                            String name = names[i];
                            if (name.equals(paramName)) {
                                removeCache(db, aliasId, i, compare, value);
                                break;
                            }
                        }
                    }
                    cursor.moveToNext();
                }
                db.setTransactionSuccessful();
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
                db.endTransaction();
            }
            mStorageHelper.close();
        }
    }

    protected void removeCache(SQLiteDatabase db, long aliasId, int paramIndex, String compare, Object value) {
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("DELETE FROM ").append(CacheStorageHelper.TABLE_CACHE).append(" WHERE ")
                .append(CacheStorageHelper.CacheTableColumn.ID).append(" IN (SELECT ");
        sqlBuilder.append(CacheStorageHelper.CacheTableColumn.ID).append(" FROM ").append(CacheStorageHelper.TABLE_CACHE)
                .append(" JOIN ").append(CacheStorageHelper.TABLE_PARAMS).append(" ON ");
        sqlBuilder.append(CacheStorageHelper.TABLE_CACHE).append(".").append(CacheStorageHelper.CacheTableColumn.ID)
                .append(" =" + " ").append(CacheStorageHelper.TABLE_PARAMS).append(".")
                .append(CacheStorageHelper.ParamsTableColumn.CACHE_ID).append(" WHERE ");
        sqlBuilder.append(CacheStorageHelper.TABLE_PARAMS).append(".").append(CacheStorageHelper.ParamsTableColumn.ALIAS_ID)
                .append(" = ? AND ");
        sqlBuilder.append(CacheStorageHelper.TABLE_PARAMS).append(".").append(CacheStorageHelper.ParamsTableColumn.PARAM)
                .append(paramIndex).append(" ").append(compare).append(" ?)");
        db.execSQL(sqlBuilder.toString(), new Object[]{aliasId, value});

        sqlBuilder = new StringBuilder();
        sqlBuilder.append("DELETE FROM ").append(CacheStorageHelper.TABLE_PARAMS).append(" WHERE ")
                .append(CacheStorageHelper.ParamsTableColumn.CACHE_ID).append(" IN (SELECT ");
        sqlBuilder.append(CacheStorageHelper.ParamsTableColumn.CACHE_ID).append(" FROM ").append(CacheStorageHelper.TABLE_PARAMS)
                .append(" LEFT JOIN ").append(CacheStorageHelper.TABLE_CACHE).append(" ON ");
        sqlBuilder.append(CacheStorageHelper.TABLE_PARAMS).append(".").append(CacheStorageHelper.ParamsTableColumn.CACHE_ID)
                .append(" =" + " ").append(CacheStorageHelper.TABLE_CACHE).append(".")
                .append(CacheStorageHelper.CacheTableColumn.ID).append(" WHERE ");
        sqlBuilder.append(CacheStorageHelper.TABLE_CACHE).append(".").append(CacheStorageHelper.CacheTableColumn.ID)
                .append(" IS NULL)");
        db.execSQL(sqlBuilder.toString());
    }
}
