function startGraph() {
    d3.json("/web-page/graph", function(error, data) {
        if (error) throw error;
        buildGraph(data);
    });
}

window.addEventListener("load", startGraph);

