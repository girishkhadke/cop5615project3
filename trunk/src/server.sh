#!/bin/bash
mv ?*.tar proj2.tar
tar xvf proj2.tar;
rm *.class
make server
make client
java server
read -p 'Server Execution Done. Press any key to read the report file:'
cat ?ep*.??? | more
make clean
