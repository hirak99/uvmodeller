cd classes
echo Manifest-Version: 1.0> ~manifest.tmp
echo Created-By: Arnab Bose>> ~manifest.tmp
echo Main-Class: UVModeller>> ~manifest.tmp
C:\java\j2sdk1.4.2\bin\jar cvfm uvmodeller.jar ~manifest.tmp .
del ~manifest.tmp
move uvmodeller.jar ..