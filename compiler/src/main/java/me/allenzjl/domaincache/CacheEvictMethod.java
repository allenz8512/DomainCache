package me.allenzjl.domaincache;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.ExecutableElement;

/**
 * The type Base method.
 */
public class CacheEvictMethod extends BasicMethod {

    public static final ClassName CACHE_STORAGE_TYPE = ClassName.get("me.allenzjl.domaincache", "CacheStorage");

    protected CacheEvict mCacheEvict;

    protected String mExpireAlias;

    public CacheEvictMethod(String packageName, String className, ExecutableElement methodElement) {
        super(packageName, className, methodElement);
        processCacheEvictAnnotation();
    }

    protected void processCacheEvictAnnotation() {
        mCacheEvict = mMethodElement.getAnnotation(CacheEvict.class);
        if (mCacheEvict != null) {
            if (ProcessUtils.isStringEmpty(mCacheEvict.value())) {
                ProcessUtils.printError("Value of attribute 'value' should not be empty", mMethodElement);
            }
            mExpireAlias = mCacheEvict.value();
        }
    }

    @Override
    public List<MethodSpec> generateMethods() {
        MethodSpec.Builder methodBuilder = ProcessUtils.overrideMethod(mMethodElement);
        addCacheEvict(methodBuilder);
        addMethodContent(methodBuilder);
        List<MethodSpec> list = new ArrayList<>(1);
        list.add(methodBuilder.build());
        return list;
    }

    protected void addCacheEvict(MethodSpec.Builder methodBuilder) {
        if (mExpireAlias != null) {
            String aliasName = mMethodName + "_a";
            methodBuilder.addStatement("String $N = $S", aliasName, mExpireAlias);
            methodBuilder.addStatement("$T.getInstance().remove($N)", CACHE_STORAGE_TYPE, aliasName);
        }
    }

    protected void addMethodContent(MethodSpec.Builder methodBuilder) {
        String paramNames = buildParamNames();
        if (mReturnType != TypeName.VOID.box()) {
            methodBuilder.addStatement("return super.$N($N)", mMethodName, paramNames);
        } else {
            methodBuilder.addStatement("super.$N($N)", mMethodName, paramNames);
        }
    }


}
