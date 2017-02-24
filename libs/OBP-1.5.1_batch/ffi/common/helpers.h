#ifndef HELPERS_H
#define HELPERS_H

#define EXCEPTION_CLASS (gExceptionClass == NULL) ? gExceptionClass = (*env)->FindClass(env,"java/lang/Exception") : gExceptionClass
#define ARG_CLASS (*env)->GetObjectClass(env, obj)
#define QUOTE(name) #name
#define ARG_NAME(idx) QUOTE(arg##idx)

#define FIELD_ID(class,idx,type) (*env)->GetFieldID(env, class, ARG_NAME(idx), type)

#define GET_IntField(idx) (*env)->GetIntField(env, obj, idx)
#define SET_IntField(idx,value) (*env)->SetIntField(env, obj, idx, value)

#define LOAD_ERROR "cannot find external function"
#define LOAD_FCT(name) \
	if (gLibHandle == NULL) gLibHandle = loadLibrary(gLibPath);\
    fctHandle = getFunctionHandle(gLibHandle, (name));\
    if (fctHandle == NULL) {\
    	(*env)->ThrowNew(env, EXCEPTION_CLASS, LOAD_ERROR name);\
    }

#endif