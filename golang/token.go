package coccoctokenizer

type Token struct {
	text string
}

func newToken(text string) Token {
	return Token{
		text: text,
	}
}

func (token *Token) GetText() string {
	return token.text
}
