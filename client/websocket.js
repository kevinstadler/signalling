function setContent (str) {
  document.getElementById("content").innerHTML = str;
}

var everConnected = false;
var socket = new WebSocket("ws://" + location.host + "/ws");
//var socket = new WebSocket("ws://127.0.0.1:8887");
var ts;

socket.onopen = function (event) {
  everConnected = true;
  ws.send("time " + new Date().toString());
}

socket.onerror = function (event) {
  if (everConnected) {
    console.log(event);
  } else {
    setContent("All tasks are currently taken.");
  }
}

function choose (element, choice) {
  socket.send(choice.toString() + new Date().valueOf()-ts);
  element.id = "selected";
  element.style.borderColor = "black";
  var rs = document.getElementsByClassName("response");
  l = rs.length;
  for (i = 0; i < l; i++) {
    rs[0].onclick = null;
    rs[0].className = "option";
  }
//  document.getElementById("delayed").style.display="none";
  document.getElementById("message").innerHTML="Sending selection to server..";
}

var stims = ['<circle />', '<circle fill="black" />'];

socket.onmessage = function (event) {
  if (event.data.substring(0,4) == "opts") {
    var str = '<p class="stimulus">' + event.data.charAt(4) + '</p><div id="delayed" style="display:none"><p id="message">Please pick one of these responses:</p>';
    for (i = 1; i+4 < event.data.length; i++) {
      str += '<div class="option response" onclick="choose(this, ' + i + ')">' + event.data.charAt(i+4) + '</div>';
    }
    setContent(str + '</div>');
    setTimeout(function(){document.getElementById("delayed").style.display = "block";ts=new Date().valueOf();},1000);
  } else if (event.data == "success") {
    document.getElementById("message").innerHTML = "Success!";
    document.getElementById("selected").style.borderColor = "green";
  } else if (event.data == "failure") {
    document.getElementById("message").innerHTML = "Failure!";
    document.getElementById("selected").style.borderColor = "red";
  } else {
    setContent(event.data);
  }
}

socket.onclose = function (event) {
  if (everConnected) {
    setContent(event.reason == "" ? "Error: lost connection to server!" : event.reason);
  } else {
    setContent("Error: could not establish connection with server");
  }
}
