package me.allenzjl.domaincache;

import javax.lang.model.element.Element;

/**
 * 注解处理异常。
 */
public class ProcessException extends RuntimeException {

    private Element e;

    public ProcessException(Element e) {
        this.e = e;
    }

    public ProcessException(String message, Element e) {
        super(message);
        this.e = e;
    }

    public ProcessException(String message, Throwable cause, Element e) {
        super(message, cause);
        this.e = e;
    }

    public ProcessException(Throwable cause, Element e) {
        super(cause);
        this.e = e;
    }

    public Element getElement() {
        return e;
    }
}
