package me.allenzjl.domaincache;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;

/**
 * Created by Allen on 2016/4/5.
 */
public class AdditionalParameter {

    protected Element mElement;

    protected boolean mMethod;

    protected String mName;

    protected CacheParameter mCacheParameter;

    protected String mAliasPrefix;

    public AdditionalParameter(Element element) {
        mElement = element;
        mMethod = element.getKind() == ElementKind.METHOD;
        mName = element.getSimpleName().toString();
        processCacheParameterAnnotation();
    }

    protected void processCacheParameterAnnotation() {
        mCacheParameter = mElement.getAnnotation(CacheParameter.class);
        mAliasPrefix = mCacheParameter.value();
        if (ProcessUtils.isStringEmpty(mAliasPrefix)) {
            mAliasPrefix = "";
        }
    }

    public Element getElement() {
        return mElement;
    }

    public boolean isMethod() {
        return mMethod;
    }

    public String getName() {
        return mName;
    }

    public String getAliasPrefix() {
        return mAliasPrefix;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AdditionalParameter that = (AdditionalParameter) o;

        return mName != null ? mName.equals(that.mName) : that.mName == null;

    }

    @Override
    public int hashCode() {
        return mName != null ? mName.hashCode() : 0;
    }
}
