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
});

function startGraph(systemName) {
    buildGraph(systemName.value);
    $("#systemsDropdownMenuButton").text(systemName.value);
}

//window.addEventListener("load", start);

