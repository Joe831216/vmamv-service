let toast = $(".toast");
let stompClient = null;

$(document).ready( function () {
    fetch("/web-page/system-names")
        .then(response => response.json())
        .then(systems => {
            let menu = $("#systemsDropdownMenu");
            Object.values(systems).forEach(systemName => {
                let sysButton = $("<button></button>")
                    .attr("class", "dropdown-item")
                    .attr("value", systemName)
                    .attr("onclick", "startGraph(this)")
                    .append(systemName);
                menu.append(sysButton);
            });
        });
        connectSocket();
});

function connectSocket() {
    let socket = new SockJS("/mgp-websocket");
    stompClient = Stomp.over(socket);
    stompClient.connect({}, function (frame) {
        toast.find("strong").empty().append("Connected");
        toast.find(".toast-body").empty().append("Successfully connected to the MGP service!");
        toast.toast('show');
        console.log("Connected: " + frame);
    });
}

function startGraph(systemName) {
    let graph = null;
    stompClient.subscribe("/topic/graph/" + systemName.value, function (message) {
        let data = JSON.parse(message.body);
        if (graph === null) {
            graph = new BuildGraph(data);
        } else {
            graph.updateData(data);
        }
    });
    stompClient.subscribe("/topic/notification/" + systemName.value, function (message) {
        let data = JSON.parse(message.body);
        toast.find("strong").empty().append(data.title);
        toast.find(".toast-body").empty().append(data.content);
        toast.toast('show');
    });
    stompClient.send("/mgp/graph/" + systemName.value);
    $("#systemsDropdownMenuButton").text(systemName.value);
}

