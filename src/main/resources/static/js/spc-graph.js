function SPCGraph(divId, title,  data) {
    let apps = Object.keys(data.rates);
    let values = Object.values(data.rates);
    let clStart = Object.keys(data.rates)[0];
    let clEnd = Object.keys(data.rates)[Object.keys(data.rates).length - 1];
    let sdRange = data.ucl - data.cl;
    let uLimit = data.ucl + sdRange;
    let lLimit = data.lcl - sdRange;
    if (lLimit < 0) { lLimit = 0 }

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
            color: 'blue',
            width: 2
        },
        marker: {
            color: '#007bff',
            size: 8,
            symbol: 'circle'
        }
    };

    // violations
    let Viol = {
        type: 'scatter',
        x: [],
        y: [],
        mode: 'markers',
        name: 'Violation',
        showlegend: true,
        marker: {
            color: 'rgb(255,65,54)',
            line: {width: 3},
            opacity: 0.5,
            size: 12,
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
            color: 'red',
            width: 2,
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
            color: 'grey',
            width: 2
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
            color: '#007bff',
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
        title: title,
        xaxis: {
            domain: [0, 0.7], // 0 to 70% of width
            zeroline: false
        },
        yaxis: {
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

    Plotly.newPlot(divId, plotData, layout, {responsive: true, showSendToCloud: false});

    this.updateData = function (data) {
        let apps = Object.keys(data.rates);
        let values = Object.values(data.rates);
        let clStart = Object.keys(data.rates)[0];
        let clEnd = Object.keys(data.rates)[Object.keys(data.rates).length - 1];
        let sdRange = data.ucl - data.cl;
        let uLimit = data.ucl + sdRange;
        let lLimit = data.lcl - sdRange;
        if (lLimit < 0) { lLimit = 0 }

        Data['x'] = apps;
        Data['y'] = values;
        histo['x'] = apps;
        histo['y'] = values;
        CL['x'] = [clStart, clEnd, null, clStart, clEnd];
        CL['y'] = [data.lcl, data.lcl, null, data.ucl, data.ucl];
        Centre['x'] = [clStart, clEnd];
        Centre['y'] = [data.cl, data.cl];

        Plotly.animate(divId, {
            data: [{x: Data['x'], y: Data['y']},
                {x: CL['x'], y: CL['y']},
                {x: Centre['x'], y: Centre['y']},
                {x: histo['x'], y: histo['y']}],
            traces: [0, 2, 3, 4]
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