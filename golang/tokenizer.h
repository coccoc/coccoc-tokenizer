#include <stdbool.h>

#ifdef __cplusplus
extern "C" { 
#endif
long long segmentPointer(const char *, bool, int, bool, int);
void freeMemory(long long);
int initialize(const char *);

#ifdef __cplusplus
}
#endif