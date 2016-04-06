package me.allenzjl.domaincache;

/**
 * Created by Allen on 2016/4/6.
 */
public @interface Observable {

    int value() default CacheStrategy.READ_CACHE_ONLY;
}
