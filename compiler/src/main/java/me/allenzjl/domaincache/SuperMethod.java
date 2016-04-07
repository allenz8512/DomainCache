package me.allenzjl.domaincache;

import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;

import java.util.List;
import java.util.Set;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;

/**
 * The type Super method.
 */
public class SuperMethod extends BasicMethod {

    public static final String SUPER_METHOD_SUFFIX = "Super";

    protected SuperMethod(String packageName, String className, ExecutableElement methodElement) {
        super(packageName, className, methodElement);
        mMethodName = mMethodElement.getSimpleName().toString() + SUPER_METHOD_SUFFIX;
        mSignature = generateSignature();
        mQualifiedName = mClassQualifiedName + "." + mSignature;
    }

    @Override
    public MethodSpec generateMethod() {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(mMethodName);

        for (TypeParameterElement typeParameterElement : mMethodElement.getTypeParameters()) {
            TypeVariable var = (TypeVariable) typeParameterElement.asType();
            methodBuilder.addTypeVariable(TypeVariableName.get(var));
        }

        methodBuilder.returns(TypeName.get(mMethodElement.getReturnType()));

        List<? extends VariableElement> parameters = mMethodElement.getParameters();
        for (VariableElement parameter : parameters) {
            TypeName type = TypeName.get(parameter.asType());
            String name = parameter.getSimpleName().toString();
            Set<Modifier> parameterModifiers = parameter.getModifiers();
            ParameterSpec.Builder parameterBuilder = ParameterSpec.builder(type, name)
                    .addModifiers(parameterModifiers.toArray(new Modifier[parameterModifiers.size()]));
            methodBuilder.addParameter(parameterBuilder.build());
        }
        methodBuilder.varargs(mMethodElement.isVarArgs());

        for (TypeMirror thrownType : mMethodElement.getThrownTypes()) {
            methodBuilder.addException(TypeName.get(thrownType));
        }

        String paramNames = buildParamNames();

        String superMethodName = mMethodElement.getSimpleName().toString();
        if (mReturnType != TypeName.VOID.box()) {
            methodBuilder.addStatement("return super.$N($N)", superMethodName, paramNames);
        } else {
            methodBuilder.addStatement("super.$N($N)", superMethodName, paramNames);
        }

        return methodBuilder.build();
    }
}
