package me.allenzjl.domaincache;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;

import java.util.List;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

/**
 * The type Basic method.
 */
public abstract class BasicMethod {

    protected String mPackageName;

    protected String mClassName;

    protected String mClassQualifiedName;

    protected ExecutableElement mMethodElement;

    protected String mMethodName;

    protected List<? extends VariableElement> mParameters;

    protected String mSignature;

    protected String mQualifiedName;

    protected TypeName mReturnType;

    protected BasicMethod(String packageName, String className, ExecutableElement methodElement) {
        mPackageName = packageName;
        mClassName = className;
        mClassQualifiedName = packageName + "." + className;
        mMethodElement = methodElement;
        mMethodName = mMethodElement.getSimpleName().toString();
        mParameters = mMethodElement.getParameters();
        mSignature = generateSignature();
        mQualifiedName = mClassQualifiedName + "." + mSignature;
        initReturnType();
    }

    protected String generateSignature() {
        StringBuilder builder = new StringBuilder(mMethodName);
        builder.append("(");
        int size = mParameters.size();
        for (int i = 0; i < size; i++) {
            VariableElement paramElement = mParameters.get(i);
            String paramClassName = TypeName.get(paramElement.asType()).toString();
            builder.append(paramClassName);
            if (i < size - 1) {
                builder.append(",");
            }
        }
        builder.append(")");
        return builder.toString();
    }

    public String getMethodName() {
        return mMethodName;
    }

    public String getQualifiedName() {
        return mQualifiedName;
    }

    protected String buildParamNames() {
        StringBuilder builder = new StringBuilder();
        int size = mParameters.size();
        for (int i = 0; i < size; i++) {
            VariableElement paramElement = mParameters.get(i);
            builder.append(paramElement.getSimpleName().toString());
            if (i < size - 1) {
                builder.append(", ");
            }
        }
        return builder.toString();
    }

    protected String buildIndexableParamNames() {
        StringBuilder builder = new StringBuilder();
        int size = mParameters.size();
        for (int i = 0; i < size; i++) {
            VariableElement paramElement = mParameters.get(i);
            TypeName paramTypeName = TypeName.get(paramElement.asType());
            if (paramTypeName.isPrimitive() || paramTypeName.equals(ClassName.get(String.class))) {
                builder.append(paramElement.getSimpleName().toString()).append(",");
            }
        }
        int length = builder.length();
        if (length > 0) {
            builder.delete(length - 1, length);
        }
        return builder.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        CacheMethod that = (CacheMethod) o;

        return mMethodName != null ? mMethodName.equals(that.mMethodName) :
                that.mMethodName == null && (mSignature != null ? mSignature.equals(that.mSignature) : that.mSignature == null);

    }

    @Override
    public int hashCode() {
        int result = mMethodName != null ? mMethodName.hashCode() : 0;
        result = 31 * result + (mSignature != null ? mSignature.hashCode() : 0);
        return result;
    }

    protected void initReturnType() {
        TypeMirror returnType = mMethodElement.getReturnType();
        mReturnType = TypeName.get(returnType);
        if (mReturnType.isPrimitive() || mReturnType == TypeName.VOID) {
            mReturnType = mReturnType.box();
        }
    }

    public abstract List<MethodSpec> generateMethods();
}
