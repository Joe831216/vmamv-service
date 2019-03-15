function run() {
    startGraph();
    //sdgGraph.updateData(getGraphData());
    //startGraph();
    //window.setInterval(updateGraph(), 5000);
}

function startGraph() {
    d3.json("/web-page/graph", function(error, data) {
        if (error) throw error;
        buildGraph(data);
    });
}

function updateGraph() {
    d3.json("/web-page/graph", function(error, data) {
        if (error) throw error;
        buildGraph().updateData(data);
        console.log("updateGraph");
        console.log(data);
    });
}

window.addEventListener("load", run);
