package me.allenzjl.domaincache;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 使用缓存的方法注解。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface Cacheable {

    String value() default "";

    int expire() default 0;

    int strategy() default CacheStrategy.READ_CACHE_ONLY;
}
