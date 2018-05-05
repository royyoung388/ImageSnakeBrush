$(document).ready(function(){
	console.log("start get");
	$.get("/webDemo/UploadServlet?number=1", function(data){
		var number = parseInt(data);
		if (number < 0) {
			number = 0;
		}
		console.log(number);
		$("#number").text(number);
		$(".time").text("离展示预计" + number * 20 +"秒");
	});
	// var timerId = setInterval(function() {
	// 	$.get("/webDemo/UploadServlet?number=1", function(data,status){
	// 		var number = parseFloat(data);
	// 		console.log(data);
	// 		$("#number").text(data);
	// 		$(".time").text("离展示预计“ + number * 20 +”秒");
	// 	});
	// }, 20000);
});