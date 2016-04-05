package me.allenzjl.domaincache;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

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

    public static final String CACHE_QUERY_WHERE_CLAUSE = CacheStorageHelper.Column.KEY +
            " = ? and " + CacheStorageHelper.Column.PARAMETER + " = ?";

    public static final String CACHE_QUERY_SQL =
            "SELECT * FROM " + CacheStorageHelper.TABLE_CACHE + " where " + CACHE_QUERY_WHERE_CLAUSE;

    public static final String CACHE_REMOVE_WHERE_CLAUSE = CacheStorageHelper.Column.ALIAS + " = ?";

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
                    long expired = cursor.getLong(cursor.getColumnIndex(CacheStorageHelper.Column.EXPIRED));
                    if (expired != 0) {
                        Date now = new Date();
                        if (now.after(new Date(expired))) {
                            return null;
                        }
                    }
                    String resultJson = cursor.getString(cursor.getColumnIndex(CacheStorageHelper.Column.RESULT));
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

    public void put(String key, Object parameter, Object result, String alias, int expire) {
        synchronized (this) {
            String parameterJson = parameter == null ? "" : JSON.toJSONString(parameter);
            Cursor cursor = null;
            try {
                SQLiteDatabase db = mStorageHelper.getWritableDatabase();
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
                values.put(CacheStorageHelper.Column.RESULT, resultJson);
                values.put(CacheStorageHelper.Column.ALIAS, alias);
                values.put(CacheStorageHelper.Column.EXPIRED, expired);
                if (count == 0) {
                    values.put(CacheStorageHelper.Column.KEY, key);
                    values.put(CacheStorageHelper.Column.PARAMETER, parameterJson);
                    db.insert(CacheStorageHelper.TABLE_CACHE, null, values);
                } else {
                    db.update(CacheStorageHelper.TABLE_CACHE, values, CACHE_QUERY_WHERE_CLAUSE, new String[]{key, parameterJson});
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
                mStorageHelper.close();
            }
        }
    }

    public void remove(String alias) {
        if (alias == null || alias.length() == 0) {
            return;
        }
        synchronized (this) {
            try {
                SQLiteDatabase db = mStorageHelper.getWritableDatabase();
                db.delete(CacheStorageHelper.TABLE_CACHE, CACHE_REMOVE_WHERE_CLAUSE, new String[]{alias});
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                mStorageHelper.close();
            }
        }
    }
}
