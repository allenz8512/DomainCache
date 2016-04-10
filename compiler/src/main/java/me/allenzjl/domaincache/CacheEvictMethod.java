package me.allenzjl.domaincache;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;

/**
 * The type Base method.
 */
public class CacheEvictMethod extends BasicMethod {

    public static final ClassName CACHE_STORAGE_TYPE = ClassName.get("me.allenzjl.domaincache", "CacheStorage");

    public static final Pattern EXPRESSION_REGEX =
            Pattern.compile("^\\s*([A-Za-z_]{1}[A-Za-z_\\d]*)\\s*(=|!=)\\s*(#?[A-Za-z_\\d.-]+)\\s*$");

    protected CacheEvict mCacheEvict;

    protected String mExpireAlias;

    protected String mExpression;

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
            String express = mCacheEvict.expr();
            if (ProcessUtils.isStringEmpty(express)) {
                express = null;
            }
            mExpression = express;
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

    @SuppressWarnings("ConstantConditions")
    protected void addCacheEvict(MethodSpec.Builder methodBuilder) {
        if (mExpireAlias != null) {
            String aliasName = mMethodName + "_a";
            methodBuilder.addStatement("String $N = $S", aliasName, mExpireAlias);
            if (mExpression == null) {
                methodBuilder.addStatement("$T.getInstance().remove($N)", CACHE_STORAGE_TYPE, aliasName);
            } else {
                Matcher matcher = EXPRESSION_REGEX.matcher(mExpression);
                if (!matcher.matches()) {
                    ProcessUtils.printError("Format of attribute 'expr':'" + mExpression + "' is illegal", mMethodElement);
                }
                String lvalue = matcher.group(1);
                String operator = matcher.group(2);
                String rvalue = matcher.group(3);
                if (!rvalue.startsWith("#")) {
                    ProcessUtils.printError("The rvalue of 'expr' should start by '#'", mMethodElement);
                }
                String valueName = rvalue.substring(1);
                VariableElement param = null;
                for (VariableElement paramElement : mParameters) {
                    if (paramElement.getSimpleName().toString().equals(valueName)) {
                        param = paramElement;
                    }
                }
                if (param == null) {
                    ProcessUtils.printError("Parameter name of '" + valueName + "' not found on this method", mMethodElement);
                }
                if (param.asType().getKind() == TypeKind.VOID) {
                    ProcessUtils.printError("Type of 'expr' rvalue should be primitive or String", mMethodElement);
                }
                TypeName paramTypeName = TypeName.get(param.asType());
                if (!paramTypeName.unbox().isPrimitive() && !paramTypeName.equals(ClassName.get(String.class))) {
                    ProcessUtils.printError("Type of 'expr' rvalue should be primitive or String", mMethodElement);
                }
                String paramName = param.getSimpleName().toString();
                methodBuilder
                        .addStatement("$T.getInstance().remove($N, $S, $S, $N)", CACHE_STORAGE_TYPE, aliasName, lvalue, operator,
                                paramName);
            }
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
