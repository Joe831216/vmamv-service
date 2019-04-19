function SPCGraph(divId, type,  data) {
    let apps = Object.keys(data.values);
    let values = Object.values(data.values);
    let viols = getViols(data.values, data.ucl);
    let clStart = Object.keys(data.values)[0];
    let clEnd = Object.keys(data.values)[Object.keys(data.values).length - 1];
    let sdRange = data.ucl - data.cl;
    let uLimit = data.ucl + sdRange;
    let lLimit = data.lcl - sdRange;
    if (lLimit < 0) { lLimit = 0 }

    function getViols(d, ucl) {
        let viols = {};
        for (let key in d) {
            if (d[key] > ucl) {
                viols[key] = d[key];
            }
        }
        return viols;
    }

    function camelCaseToSentenceCase(text) {
        let spaced = text.replace( /([A-Z])/g, " $1" );
        return spaced.charAt(0).toUpperCase() + spaced.slice(1);
    }

    // data
    let Data = {
        type: 'scatter',
        x: apps,
        y: values,
        mode: 'markers',
        name: 'Data',
        showlegend: true,
        hoverinfo: 'all',

        line: {
            simplify: false,
            color: 'blue',
            width: 2
        },
        marker: {
            colorscale: 'Portland',
            color: values,
            size: 12,
            symbol: 'circle'
        }
    };

    // violations
    let Viol = {
        type: 'scatter',
        x: Object.keys(viols),
        y: Object.values(viols),
        mode: 'markers',
        name: 'Violation',
        showlegend: true,
        marker: {
            color: 'rgb(255,65,54)',
            line: {width: 3},
            opacity: 0.5,
            size: 16,
            symbol: 'circle-open'
        }
    };

    // control limits
    let CL = {
        type: 'scatter',
        x: [clStart, clEnd, null, clStart, clEnd],
        y: [data.lcl, data.lcl, null, data.ucl, data.ucl],
        mode: 'lines',
        name: 'LCL/UCL',
        showlegend: true,
        line: {
            simplify: false,
            color: '#F6697D',
            width: 1,
            dash: 'dash'
        }
    };

    // centre
    let Centre = {
        type: 'scatter',
        x: [clStart, clEnd],
        y: [data.cl, data.cl],
        mode: 'lines',
        name: 'Centre',
        showlegend: true,
        line: {
            simplify: false,
            color: '#CFC5C9',
            width: 1
        }
    };

    // histogram on axis 2
    let histo = {
        type: 'histogram',
        x: apps,
        y: values,
        name: 'Distribution',
        orientation: 'h',
        marker: {
            color: '#3AA0E4',
            line: {
                color: 'white',
                width: 1
            }
        },
        xaxis: 'x2',
        yaxis: 'y2'
    };


    // all traces
    let plotData = [Data, Viol,CL,Centre,histo];

    // layout
    let layout = {
        title: "Control Chart - " + camelCaseToSentenceCase(type),
        margin: {pad: 3},
        xaxis: {
            title: 'Services',
            domain: [0, 0.7], // 0 to 70% of width
            zeroline: false
        },
        yaxis: {
            title: camelCaseToSentenceCase(type),
            range: [lLimit,uLimit],
            zeroline: true
        },
        xaxis2: {
            domain: [0.8, 1] // 70 to 100% of width
        },
        yaxis2: {
            range: [lLimit,uLimit],
            anchor: 'x2',
            showticklabels: false
        }
    };

    Plotly.newPlot(divId, plotData, layout, {responsive: true, showSendToCloud: true});

    this.updateData = function (data) {
        let apps = Object.keys(data.values);
        let values = Object.values(data.values);
        let viols = getViols(data.values, data.ucl);
        let clStart = Object.keys(data.values)[0];
        let clEnd = Object.keys(data.values)[Object.keys(data.values).length - 1];
        let sdRange = data.ucl - data.cl;
        let uLimit = data.ucl + sdRange;
        let lLimit = data.lcl - sdRange;
        if (lLimit < 0) { lLimit = 0 }

        Data['x'] = apps;
        Data['y'] = values;
        Data['marker']['color'] = values;
        Viol['x'] = Object.keys(viols);
        Viol['y'] = Object.values(viols);
        histo['x'] = apps;
        histo['y'] = values;
        CL['x'] = [clStart, clEnd, null, clStart, clEnd];
        CL['y'] = [data.lcl, data.lcl, null, data.ucl, data.ucl];
        Centre['x'] = [clStart, clEnd];
        Centre['y'] = [data.cl, data.cl];

        Plotly.animate(divId, {
            data: [{x: Data['x'], y: Data['y'], marker: { color: Data['marker']['color'] }},
                {x: Viol['x'], y: Viol['y']},
                {x: CL['x'], y: CL['y']},
                {x: Centre['x'], y: Centre['y']},
                {x: histo['x'], y: histo['y']}],
            traces: [0, 1, 2, 3, 4],
            layout: {}
        }, {
            transition: {
                duration: 500,
                easing: 'cubic-in-out'
            },
            frame: {
                duration: 500
            }
        });

        Plotly.animate(divId, {
            layout: {
                yaxis: {
                    range: [lLimit,uLimit]
                },
                yaxis2: {
                    range: [lLimit,uLimit]
                }
            }
        }, {
            transition: {
                duration: 500,
                easing: 'cubic-in-out'
            }
        });
    };
}