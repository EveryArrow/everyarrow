CC = gcc
CFLAGS = -Wall -Wextra -std=c11 -I include
SRC = src/dittoe.c
OBJ = build/dittoe.o
LIB = build/libdittoe.a
TEST_SRC = tests/test_dittoe.c
TEST_BIN = build/test_dittoe

.PHONY: all test clean

all: $(LIB)

build:
	mkdir -p build

$(OBJ): $(SRC) include/dittoe.h | build
	$(CC) $(CFLAGS) -c $(SRC) -o $(OBJ)

$(LIB): $(OBJ)
	ar rcs $(LIB) $(OBJ)

$(TEST_BIN): $(TEST_SRC) $(LIB)
	$(CC) $(CFLAGS) $(TEST_SRC) -L build -ldittoe -o $(TEST_BIN)

test: $(TEST_BIN)
	./$(TEST_BIN)

clean:
	rm -rf build
