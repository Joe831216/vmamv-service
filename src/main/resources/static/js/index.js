const NOTI_LEVEL_INFO = "info";
const NOTI_LEVEL_WARNING = "warning";
const NOTI_LEVEL_ERROR = "error";

let toast = $("#toast-div .toast");
let sdgCanvas = $("#sdg-canvas");
let spcCanvas = $("#spc-canvas");
let showControlChart = $("#graph-show-control-chart");
let spcTypesInput = $('#spcGraphOptionsMenuLink').parent().find("input[name='controlChartType']");
let notificationsDropdown = $("#notificationsMenuLink").parent().find(".dropdown-menu");
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
        toast.find(".toast-header")
            .attr("class", "toast-header text-white bg-primary")
            .prepend("<i class='fas fa-info-circle mr-2'></i>");
        toast.find("strong").empty().append("Connected");
        toast.find(".toast-body").empty().append("Successfully connected to the MGP service!");
        toast.toast('show');
        console.log("Connected: " + frame);
    });
}

let addInlineStyle = function(children) {
    for (let i = 0; i < children.length; i++) {
        let child = children[i];
        if (child instanceof Element) {
            let cssText = '';
            let computedStyle = window.getComputedStyle(child, null);
            for (let i = 0; i < computedStyle.length; i++) {
                let prop = computedStyle[i];
                cssText += prop + ':' + computedStyle.getPropertyValue(prop) + ';';
            }
            child.setAttribute('style', cssText);
            addInlineStyle(child.childNodes);
        }
    }
};

let downloadGraphLink =  $("#download-graph");

downloadGraphLink.ready = false;
downloadGraphLink.click(function () {
    if (!downloadGraphLink.ready) {
        event.preventDefault();
        graph.stopSimulation();
        let svg = document.querySelector("#sdg-canvas").cloneNode(true);
        document.body.appendChild(svg);
        addInlineStyle(svg.childNodes);
        html2canvas(svg).then(canvas => {
            svg.remove();
            graph.restartSimulation();
            downloadGraphLink.attr("href", canvas.toDataURL("image/png"), 1.0);
            let dt = new Date();
            downloadGraphLink.attr("download", "SDG_" + startGraph.systemName + "_" +  dt.getFullYear() + "-" + dt.getMonth() + "-" + dt.getDate() + ".png");
            downloadGraphLink.ready = true;
            this.click();
        });
    } else {
        downloadGraphLink.ready = false;
    }
});

function exportSVG() {
    const svg = document.querySelector("#graph").cloneNode(true);
    document.body.appendChild(svg);
    const g = svg.querySelector("g");
    svg.setAttribute("width", g.getBBox().width);
    svg.setAttribute("height", g.getBBox().height);
    const svgAsXML = (new XMLSerializer).serializeToString(svg);
    const svgData = `data:image/svg+xml,${encodeURIComponent(svgAsXML)}`;
    downloadGraphLink.attr("href", svgData);
    downloadGraphLink.attr("download", "graph.svg");
}

let subscribeGraph = null;
let subscribeNotify = null;
let graph = null;

function startGraph(systemName) {
    startGraph.systemName = systemName.value;

    $("#systemsDropdownMenuButton")
        .text(systemName.value);

    $("#system-options-menu-button").prop("disabled", false);

    $("#systemsDropdownMenu button.active")
        .removeClass("active")
        .css("pointer-events", "auto");

    $(systemName).addClass("active")
        .css("pointer-events", "none");

    // If graph exist, unsubscribe and clear graph.
    if (subscribeGraph !== null && graph !== null) {
        subscribeGraph.unsubscribe();
        graph.closeNodeCard();
        graph = null;
        $("#graph g").remove();
    }

    if (subscribeNotify !== null) {
        subscribeNotify.unsubscribe();
    }

    // Subscribe graph topic
    subscribeGraph = stompClient.subscribe("/topic/graph/" + systemName.value, function (message) {
        let data = JSON.parse(message.body);
        if (graph === null) {
            graph = new SDGGraph(data);
        } else {
            graph.updateData(data);
        }
    });

    let notificationCount = 0;

    function createNotificationDropdownItem(notification) {
        let headerClass = "toast-header ";
        let icon;
        if (notification.level === NOTI_LEVEL_INFO) {
            headerClass += "text-white bg-primary";
            icon = "<i class='fas fa-info-circle mr-2'></i>";
        } else if (notification.level === NOTI_LEVEL_WARNING) {
            headerClass += "text-white bg-warning";
            icon = "<i class='fas fa-exclamation-triangle mr-2'></i>";
        } else if (notification.level === NOTI_LEVEL_ERROR) {
            headerClass += "text-white bg-danger";
            icon = "<i class='fas fa-bug mr-2'></i>";
        }

        return "<button class='dropdown-item'>" +
            "<div class='toast show'>" +
            "<div class='" + headerClass + "'>" +
            icon +
            "<strong class='mr-auto'>" + notification.title + "</strong>" +
            "<small>" + notification.dateTime + "</small>" +
            "</div>" +
            "<div class='toast-body'>" + notification.content + "</div>" +
            "</div>" +
            "</button>";
    }

    function removeNotificationDropdownItem() {
        if (notificationCount > 100) {
            notificationsDropdown.find(".dropdown-item:gt(99)").remove();
        }
    }

    // Fetch current system notifications
    fetch("/web-page/notification/" + systemName.value)
        .then(response => response.json())
        .then(notifications => {
            notificationCount += notifications.length;
            notificationsDropdown.empty();
            notifications.forEach(notification => {
                let item = $(createNotificationDropdownItem(notification));
                if (notification.appName && notification.version) {
                    item.click(function () {
                        graph.clickNodeByNameAndVersion(notification.appName, notification.version)
                    });
                }
                notificationsDropdown.append(item);
            });
        });

    // Subscribe notification topic
    subscribeNotify = stompClient.subscribe("/topic/notification/" + systemName.value, function (message) {
        let data = JSON.parse(message.body);
        toast.find("i").remove();
        if (data.level === NOTI_LEVEL_INFO) {
            toast.find(".toast-header")
                .attr("class", "toast-header text-white bg-primary")
                .prepend("<i class='fas fa-info-circle mr-2'></i>");
        } else if (data.level === NOTI_LEVEL_WARNING) {
            toast.find(".toast-header")
                .attr("class", "toast-header text-white bg-warning")
                .prepend("<i class='fas fa-exclamation-triangle mr-2'></i>");
        } else if (data.level === NOTI_LEVEL_ERROR) {
            toast.find(".toast-header")
                .attr("class", "toast-header text-white bg-danger")
                .prepend("<i class='fas fa-bug mr-2'></i>");
        }
        toast.find("strong").empty().append(data.title);
        toast.find(".toast-body").empty().append(data.content);
        toast.toast('show');

        notificationCount++;
        let item = $(createNotificationDropdownItem(data));
        if (data.appName && data.version) {
            item.click(function () {
                graph.clickNodeByNameAndVersion(data.appName, data.version)
            });
        }
        notificationsDropdown.prepend(item);
        removeNotificationDropdownItem();
    });

    stompClient.send("/mgp/graph/" + systemName.value);

    if (showControlChart[0].checked) {
        let type = getSpcType();
        if (type) {
            startSPCGraph(startGraph.systemName, type);
        }
    }
}

function getSpcType() {
    let type;
    spcTypesInput.each(function () {
        if (this.checked) {
            type = this.value;
            return false;
        }
    });
    return type;
}

let subscribeSpcGraph = null;
let spcGraph = null;

showControlChart.on("change", function () {
    if (this.checked) {
        let type = getSpcType();
        if (type) {
            sdgCanvas.addClass("split-up");
            spcCanvas.addClass("split-down");
            spcCanvas.removeClass("collapse");
            startSPCGraph(startGraph.systemName, type);
        }
    } else {
        spcCanvas.addClass("collapse");
        sdgCanvas.removeClass("split-up");
        spcCanvas.removeClass("split-down");
        spcGraph = null;
        spcCanvas.empty();
    }
    window.dispatchEvent(new Event('resize'));
});

spcTypesInput.on("change", function () {
    if (showControlChart[0].checked) {
        let type = getSpcType();
        if (type) {
            sdgCanvas.addClass("split-up");
            spcCanvas.addClass("split-down");
            spcCanvas.removeClass("collapse");
            startSPCGraph(startGraph.systemName, type);
        }
    }
    window.dispatchEvent(new Event('resize'));
});

function startSPCGraph(systemName, type) {
    if (subscribeSpcGraph !== null) {
        subscribeSpcGraph.unsubscribe();
    }

    if (spcGraph !== null) {
        spcGraph = null;
        spcCanvas.empty();
    }

    subscribeSpcGraph = stompClient.subscribe("/topic/graph/spc/" + type + "/" + systemName, function (message) {
        let data = JSON.parse(message.body);
        if (spcGraph === null) {
            spcGraph = new SPCGraph(spcCanvas.prop('id'), type, data);
        } else {
            spcGraph.updateData(data);
        }
    });

    stompClient.send("/mgp/graph/spc/" + type + "/" + systemName);
}

