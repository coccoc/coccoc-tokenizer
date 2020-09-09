package coccoctokenizer

import (
	"unsafe"
)

func unsafeGetInt32(a uintptr) int32 {
	return *(*int32)(unsafe.Pointer(a))
}

func unsafeGetInt64(a uintptr) int64 {
	return *(*int64)(unsafe.Pointer(a))
}
func unsafeGetRune(a uintptr) rune {
	return *(*rune)(unsafe.Pointer(a))
}
