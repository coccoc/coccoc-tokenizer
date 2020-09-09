package coccoctokenizer

//#cgo CFLAGS: -I /home/anhhvd/git/coccoc-tokenizer/tokenizer
//#include "tokenizer.h"
import "C"

import (
	"github.com/pkg/errors"
)

const (
	DICT_PATH       = "/usr/share/tokenizer/dicts"
	TOKENIZE_NORMAL = 0
	TOKENIZE_HOST   = 1
	TOKENIZE_URL    = 2
)

var (
	errTokenizerCannotBeInitialized = errors.New("Tokenizer cannot be initialized")
)

type Tokenizer struct{}

var (
	tokenizerInstance *Tokenizer
)

func (tokenizer *Tokenizer) initialize(path string) int {
	cpath := C.CString(path)
	return int(C.initialize(cpath))
}
func (tokenizer *Tokenizer) segmentPointer(text string, forTransforming bool, tokenizerOption int, keepPuncts bool) int64 {
	ctext := C.CString(text)
	cforTransforming := C.bool(forTransforming)
	ctokenizerOption := C.int(tokenizerOption)
	ckeepPuncts := C.bool(keepPuncts)
	textLength := len([]rune(text))
	ctextLength := C.int(textLength)
	cres := C.segmentPointer(ctext, cforTransforming, ctokenizerOption, ckeepPuncts, ctextLength)
	return int64(cres)
}

func (tokenizer *Tokenizer) freeMemory(resPointer int64) {
	C.freeMemory(C.longlong(resPointer))
}

//Instance return an instance of tokenizer
func Instance() (*Tokenizer, error) {
	if tokenizerInstance == nil {
		status := tokenizerInstance.initialize(DICT_PATH)
		if 0 > status {
			return nil, errTokenizerCannotBeInitialized
		}
	}
	return tokenizerInstance, nil
}

func (tokenizer *Tokenizer) Segment(text string, forTransforming bool, tokenizeOption int, keepPuncts bool) []Token {
	if len(text) == 0 {
		return nil
	}
	resPointer := tokenizer.segmentPointer(text, forTransforming, tokenizeOption, keepPuncts)
	normalizedStringPointer := unsafeGetInt64(uintptr(resPointer) + 8)
	rangesSize := unsafeGetInt32(uintptr(resPointer) + uintptr(8*2))
	rangesDataPointer := unsafeGetInt64(uintptr(resPointer) + uintptr(8*3))

	spacePositionsSize := unsafeGetInt32(uintptr(resPointer) + 8*5)
	spacePositionsDataPointer := unsafeGetInt64(uintptr(resPointer) + 8*6)

	spacePositions := make([]int32, spacePositionsSize+1)
	for i := int32(0); i < spacePositionsSize; i++ {
		spacePositions[i] = unsafeGetInt32(uintptr(spacePositionsDataPointer) + uintptr(i)*4)
	}
	spacePositions[spacePositionsSize] = -1
	tokenSize := 4 * 6
	tokens := make([]Token, 0)
	for i, spacePos := 0, 0; i < int(rangesSize); i++ {
		startPos := unsafeGetInt32(uintptr(rangesDataPointer) + uintptr(i)*uintptr(tokenSize))
		endPos := unsafeGetInt32(uintptr(rangesDataPointer) + uintptr(i)*uintptr(tokenSize) + 4)
		sb := make([]rune, 0)
		for j := startPos; j < endPos; j++ {
			if int32(j) == spacePositions[spacePos] {
				if forTransforming {
					sb = append(sb, '_')
				} else {
					sb = append(sb, ' ')
				}
			}
			sb = append(sb, unsafeGetRune(uintptr(normalizedStringPointer)+uintptr(j)*4))
		}
		tokens = append(tokens, newToken(string(sb)))
	}
	tokenizer.freeMemory(resPointer)
	return tokens
}
