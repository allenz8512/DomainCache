package me.allenzjl.domaincache;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.tools.Diagnostic;

/**
 * 注解处理帮助类
 */
public class ProcessUtils {

    public static final ClassName OVERRIDE = ClassName.get(Override.class);

    private static ProcessingEnvironment processingEnv;

    public static synchronized void init(ProcessingEnvironment processingEnv) {
        ProcessUtils.processingEnv = processingEnv;
    }

    public static ProcessingEnvironment getProcessingEnv() {
        return processingEnv;
    }

    /**
     * 返回一个覆盖了方法的构建器。
     *
     * @param method 方法元素
     * @return 方法构建器
     */
    public static MethodSpec.Builder overrideMethod(ExecutableElement method) {
        if (method == null) {
            throw new NullPointerException("method == null");
        }

        Set<Modifier> modifiers = method.getModifiers();
        if (modifiers.contains(Modifier.PRIVATE) || modifiers.contains(Modifier.FINAL) || modifiers.contains(Modifier.STATIC)) {
            throw new IllegalArgumentException("cannot override method with modifiers: " + modifiers);
        }

        String methodName = method.getSimpleName().toString();
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName);

        methodBuilder.addAnnotation(OVERRIDE);

        modifiers = new LinkedHashSet<>(modifiers);
        modifiers.remove(Modifier.ABSTRACT);
        methodBuilder.addModifiers(modifiers);

        for (TypeParameterElement typeParameterElement : method.getTypeParameters()) {
            TypeVariable var = (TypeVariable) typeParameterElement.asType();
            methodBuilder.addTypeVariable(TypeVariableName.get(var));
        }

        methodBuilder.returns(TypeName.get(method.getReturnType()));

        List<? extends VariableElement> parameters = method.getParameters();
        for (VariableElement parameter : parameters) {
            TypeName type = TypeName.get(parameter.asType());
            String name = parameter.getSimpleName().toString();
            Set<Modifier> parameterModifiers = parameter.getModifiers();
            ParameterSpec.Builder parameterBuilder = ParameterSpec.builder(type, name)
                    .addModifiers(parameterModifiers.toArray(new Modifier[parameterModifiers.size()]));
            methodBuilder.addParameter(parameterBuilder.build());
        }
        methodBuilder.varargs(method.isVarArgs());

        for (TypeMirror thrownType : method.getThrownTypes()) {
            methodBuilder.addException(TypeName.get(thrownType));
        }

        return methodBuilder;
    }

    public static String getTypeQualifiedName(TypeElement typeElement) {
        String packageName =
                ProcessUtils.getProcessingEnv().getElementUtils().getPackageOf(typeElement).getQualifiedName().toString();
        String typeName = typeElement.getSimpleName().toString();
        return packageName + "." + typeName;
    }

    public static boolean isStringEmpty(String str) {
        return str == null || str.length() == 0;
    }

    public static void print(String message) {
        print(message, null);
    }

    public static void print(String message, Element e) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, message, e);
    }

    public static void printError(String message, Element e) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, e);
        throw new ProcessException(message, e);
    }
}
