package me.allenzjl.domaincache;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

/**
 * The type Observable class.
 */
public class ObservableClass extends BasicClass {

    public static final String OBSERVABLE_CLASS_NAME_SUFFIX = "Observable";

    public static final String DOMAIN_PROXY_FIELD_NAME = "proxy";

    public ObservableClass(TypeElement classElement) {
        super(classElement, OBSERVABLE_CLASS_NAME_SUFFIX);
    }

    @Override
    protected TypeSpec generateClass() {
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(mClassName).addModifiers(Modifier.PUBLIC);

        ClassName proxyTypeName = ClassName.get(mPackageName, mClassElement.getSimpleName() + CacheClass.CACHE_CLASS_NAME_SUFFIX);
        FieldSpec proxyField = FieldSpec.builder(proxyTypeName, DOMAIN_PROXY_FIELD_NAME, Modifier.PROTECTED).build();
        classBuilder.addField(proxyField);

        MethodSpec constructor =
                MethodSpec.constructorBuilder().addParameter(proxyTypeName, DOMAIN_PROXY_FIELD_NAME).addModifiers(Modifier.PUBLIC)
                        .addStatement("this.$N = $N", DOMAIN_PROXY_FIELD_NAME, DOMAIN_PROXY_FIELD_NAME).build();
        classBuilder.addMethod(constructor);

        for (BasicMethod method : mMethods) {
            for (MethodSpec methodSpec : method.generateMethods()) {
                classBuilder.addMethod(methodSpec);
            }
        }
        return classBuilder.build();
    }
}
