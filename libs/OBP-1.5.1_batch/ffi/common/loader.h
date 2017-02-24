#ifndef LOADER_H
#define LOADER_H

typedef void* libhandle_ptr;

libhandle_ptr loadLibrary(char *inLibPath);

void unloadLibrary(libhandle_ptr inHandle);

void * getFunctionHandle(libhandle_ptr inLibHandle, char *inFctName);

#endif