

global.a = 0
global.b = 0
global.c = 0
global.d = 0

threads.start(function(){
    sleep(100)
    a = 4
});

threads.runAsync(function(){
    sleep(100)
    return 5
}).then((vv)=>{
    b = vv + 1
})

threads.runOnIoThreadPool(function(){
    sleep(200)
    c = 7
})

threads.runOnThreadPool(function(){
    sleep(300)
    d = 99
})