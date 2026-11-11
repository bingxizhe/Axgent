let { requestPermission, bash, sh } = require('termux');

requestPermission((b)=>{
 console.assert(b,'Termux permission granted');
 test1();
 test2()
})

function test1(){
    bash('echo "Hello from Termux bash!"',(r)=>{
        console.assert(r.stdout.length>0);
    })
}

function test2(){
    bash('echo "Hello from Termux bash!"',{background:true},(r)=>{
        console.assert(r.stdout === "Hello from Termux bash!");
    })
}