CC = gcc
CFLAGS = -Wall -ansi -pedantic -O2 -g
SOURCE = *.c *.h *akefile*
PROGRAM = ex1.getTool

all:	$(PROGRAM)

$(PROGRAM):	ex1.getTool.c
	$(CC) $(CFLAGS) -o ex1.getTool ex1.getTool.c

tarfile:
	tar cf $(PROGRAM).tar $(SOURCE)
	gzip $(PROGRAM).tar

zipfile:
	zip -k $(PROGRAM).zip $(SOURCE)

clean:
	rm -f *.o core $(PROGRAM)

