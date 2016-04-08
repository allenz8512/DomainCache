package me.allenzjl.domaincache;

import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.lang.model.element.TypeElement;

/**
 * The type Basic class.
 */
public abstract class BasicClass {

    protected TypeElement mClassElement;

    protected String mPackageName;

    protected String mClassName;

    protected Set<BasicMethod> mMethods;

    protected boolean mJavaFileGenerated = false;

    public BasicClass(TypeElement classElement, String classNameSuffix) {
        mClassElement = classElement;
        mPackageName = ProcessUtils.getProcessingEnv().getElementUtils().getPackageOf(classElement).getQualifiedName().toString();
        mClassName = classElement.getSimpleName() + classNameSuffix;
        mMethods = new LinkedHashSet<>();
    }

    public TypeElement getClassElement() {
        return mClassElement;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public String getClassName() {
        return mClassName;
    }

    public String getQualifiedName() {
        return mPackageName + "." + mClassName;
    }

    public void addMethod(BasicMethod cacheMethod) {
        if (!mMethods.contains(cacheMethod)) {
            mMethods.add(cacheMethod);
        }
    }

    public boolean isJavaFileGenerated() {
        return mJavaFileGenerated;
    }

    public void generateJavaFile() {
        if (mJavaFileGenerated) {
            return;
        }
        TypeSpec classSpec = generateClass();
        JavaFile javaFile = JavaFile.builder(mPackageName, classSpec).build();
        try {
            javaFile.writeTo(ProcessUtils.getProcessingEnv().getFiler());
            mJavaFileGenerated = true;
        } catch (IOException e) {
            throw new ProcessException(e, null);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        CacheClass that = (CacheClass) o;

        return mPackageName != null ? mPackageName.equals(that.mPackageName) :
                that.mPackageName == null && (mClassName != null ? mClassName.equals(that.mClassName) : that.mClassName == null);

    }

    @Override
    public int hashCode() {
        int result = mPackageName != null ? mPackageName.hashCode() : 0;
        result = 31 * result + (mClassName != null ? mClassName.hashCode() : 0);
        return result;
    }

    protected abstract TypeSpec generateClass();
}
