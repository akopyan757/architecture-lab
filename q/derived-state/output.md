```
kotlinc 2.4.20
A: хранимое поле, писатели разрозненные
1 add milk                   items=100    stored=100   
2 server prices milk=120     items=120    stored=100      <- расхождение
3 add bread                  items=170    stored=150      <- расхождение
4 remove bread               items=120    stored=120   

B: хранимое поле, единый reduce()
1 add milk                   items=100    stored=100   
2 server prices milk=120     items=120    stored=120   
3 add bread                  items=170    stored=170   
4 restore milk x2            items=240    stored=170      <- расхождение
5 copy() мимо reduce         items=290    stored=170      <- расхождение

C: вычисление на чтении
1 add milk                   items=100    stored=100   
2 server prices milk=120     items=120    stored=120   
3 add bread                  items=170    stored=170   
4 restore milk x2            items=240    stored=240   
5 copy() мимо reduce         items=290    stored=290   
```
