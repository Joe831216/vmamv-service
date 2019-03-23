function BuildGraph(data) {
    this.data = data;

    const LABEL_SERVICE = "Service";
    const LABEL_NULLSERVICE = "NullService";
    const LABEL_ENDPOINT = "Endpoint";
    const LABEL_NULLENDPOINT = "NullEndpoint";
    const LABEL_QUEUE = "Queue";
    const LABEL_OUTDATEDVERSION = "OutdatedVersion";

    const REL_OWN = "OWN";
    const REL_HTTPREQUEST = "HTTP_REQUEST";
    const REL_AMQPPUBLISH = "AMQP_PUBLISH";
    const REL_AMQPSUBSCRIBE = "AMQP_SUBSCRIBE";
    const REL_NEWERPATCHVERSION = "NEWER_PATCH_VERSION";

    const SYMBOL_SERVIVE = d3.symbolSquare;
    const SYMBOL_ENDPOINT = d3.symbolCircle;
    const SYMBOL_QUEUE = d3.symbolHexagonAlt;

    const SIZE_SERVIVE = 4500;
    const SIZE_ENDPOINT = 1500;
    const SIZE_QUEUE = SIZE_SERVIVE;

    const COLOR_NULL = "#3a3a3a";
    const COLOR_QUEUE = "#85d18c";
    const COLOR_WARNING = "orange";

    const NODE_SCALE = 1.5;

    let canvas = document.getElementById("canvas");

    let graph = document.getElementById("graph");
    graph.setAttribute("width", canvas.clientWidth);
    graph.setAttribute("height", canvas.clientHeight);

    let body = document.body;
    body.setAttribute("height", canvas.clientHeight);

    let svg = d3.select("svg");

    let graphWidth = +svg.attr("width");
    let graphHeight = +svg.attr("height");

    function resize() {
        graph.setAttribute("width", canvas.clientWidth);
        graph.setAttribute("height", canvas.clientHeight);
        graphWidth = svg.attr("width");
        graphHeight = svg.attr("height");
    }

    window.addEventListener("resize", resize);

    let zoom = d3.zoom()
        .scaleExtent([0.1, 5])
        .on("zoom", zoomed);

    function zoomed() {
        let transform = d3.event.transform;
        g.attr("transform", transform);
    }

    svg.call(zoom);

    let simulation = d3.forceSimulation(data.nodes)
        .force("link", d3.forceLink(data.links)
            .id(d => d.id)
            .distance(180)
            .strength(2))
        .force("charge", d3.forceManyBody()
            .strength(-2000)
            .distanceMax(1000))
        //.force("center", d3.forceCenter(graphWidth / 2, graphHeight / 2))
        .force("x", d3.forceX(graphWidth / 2))
        .force("y", d3.forceY(graphHeight / 2))
        .force("collision", d3.forceCollide().radius(90).strength(0.7))
        .velocityDecay(0.8)
        .alphaTarget(0.2)
        .on("tick", ticked);

    let t = d3.transition().duration(600);
    let td = d3.transition().duration(600).delay(500);

    let color = d3.scaleOrdinal(d3.schemeSet2);

    let g = svg.append("g");

    let link = g.append("g").attr("class", "links").selectAll("line");

    let node = g.append("g").attr("class", "nodes").selectAll("path");

    let nodelabel = g.append("g").attr("class", "node-labels").selectAll("g");

    let enterOrExitEvent = true;

    update();

    this.updateData = function (d) {
        let oldData = $.extend(true, {}, data);

        // REMOVE old nodes
        oldData.nodes.forEach(oldNode => {
            let exist = false;
            d.nodes.some(newNode => {
                if (oldNode.id === newNode.id) {
                    exist = true;
                    return true;
                }
            });
            if (!exist) {
                //console.log("Remove node: ");
                //console.log(oldNode);
                data.nodes.splice(data.nodes.findIndex(node => node.id === oldNode.id), 1);
                enterOrExitEvent = true;
            }
        });

        // UPDATE old nodes
        data.nodes.forEach((oldNode) => {
            let newNode = d.nodes.find(node => node.id === oldNode.id);
            //console.log(JSON.stringify(oldNode.labels.sort()) + "vs" + JSON.stringify(newNode.labels.sort()));
            //console.log(!(JSON.stringify(oldNode.labels.sort()) === JSON.stringify(newNode.labels.sort())));
            if ((JSON.stringify(oldNode.labels.sort()) !== JSON.stringify(newNode.labels.sort()))) {
                //console.log("Labels update: " + newNode.id);
                //console.log(newNode.labels);
                oldNode.labels = newNode.labels;
            }
            if (oldNode.number !== newNode.number) {
                oldNode.number = newNode.number;
            }
        });

        // ADD new nodes
        d.nodes.forEach(newNode => {
            let exist = false;
            oldData.nodes.some(oldNode => {
                if (newNode.id === oldNode.id) {
                    exist = true;
                    return true;
                }
            });
            if (!exist) {
                //console.log("Add node: ");
                //console.log(newNode);
                data.nodes.push(newNode);
                enterOrExitEvent =true;
            }
        });

        // REMOVE old links
        oldData.links.forEach(oldLink => {
            let exist = false;
            d.links.some(newLink => {
                if (oldLink.type === newLink.type && oldLink.source.id === newLink.source && oldLink.target.id === newLink.target) {
                    exist = true;
                    return true;
                }
            });
            if (!exist) {
                //console.log("Remove link:");
                //console.log(oldLink);
                data.links.splice(data.links.findIndex(link => link.type === oldLink.type && link.source.id === oldLink.source.id && link.target.id === oldLink.target.id), 1);
                enterOrExitEvent = true;
            }
        });
        // ADD new links
        d.links.forEach(newLink => {
            let exist = false;
            oldData.links.some(oldLink => {
                if (oldLink.type === newLink.type && oldLink.source.id === newLink.source && oldLink.target.id === newLink.target) {
                    exist = true;
                    return true;
                }
            });
            if (!exist) {
                //console.log("Add link:");
                //console.log(newLink);
                data.links.push(newLink);
                enterOrExitEvent = true;
            }
        });
        update();
    };

    function update() {
        //console.log(data);
        console.log("update");

        simulation.nodes(data.nodes);
        simulation.force("link").links(data.links);

        // JOIN data and event listeners with old links
        link = link.data(data.links, d => { return d.type + ":" + d.source.id + "-" + d.target.id });

        // EXIT old links
        link.exit().transition(t)
            .attr("stroke-width", 0)
            .remove();

        // ENTER new links
        let linkEnter = link.enter().append("g");

        linkEnter.append("line")
            .attr("stroke-width", 0)
            .attr("marker-end", d => {
                if (d.type === REL_HTTPREQUEST || d.type === REL_AMQPPUBLISH || d.type === REL_AMQPSUBSCRIBE) {
                    if (d.target.labels.includes(LABEL_SERVICE) || d.target.labels.includes(LABEL_QUEUE)) {
                        return"url(#arrow-l)"
                    } else {
                        return "url(#arrow-m)";
                    }
                } else if (d.type === REL_NEWERPATCHVERSION) {
                    return"url(#arrow-l-warning)"
                }
            })
            .style("stroke", d => {
                if (d.type === REL_NEWERPATCHVERSION) {
                    return COLOR_WARNING;
                }
            })
            .call(function(link) { link.transition(td).attr("stroke-width", 3); });

        linkEnter.append("text")
            .text(d => {return d.type});

        link = linkEnter.merge(link);

        // JOIN data and event listeners with old nodes
        node = node.data(data.nodes, function(d) { return d.id;});
        //node = node.data(data.nodes);

        // EXIT old nodes
        node.exit().transition(t)
            .attr("fill-opacity", 0)
            .remove();

        // UPDATE old nodes
        node.transition(t)
            .attr("fill", d => {
                if (d.labels.includes(LABEL_NULLSERVICE) || d.labels.includes(LABEL_NULLENDPOINT)) {
                    return COLOR_NULL;
                } else if (d.labels.includes(LABEL_QUEUE)) {
                    return COLOR_QUEUE;
                } else {
                    return color(d.appName);
                }
            });

        // ENTER new nodes
        let nodeEnter = node.enter().append("path")
            .attr("fill", d => {
                if (d.labels.includes(LABEL_NULLSERVICE) || d.labels.includes(LABEL_NULLENDPOINT)) {
                    return COLOR_NULL;
                } else if (d.labels.includes(LABEL_QUEUE)) {
                    return COLOR_QUEUE;
                } else {
                    return color(d.appName);
                }
            })
            .attr("stroke", d => {
                if (d.labels.includes(LABEL_OUTDATEDVERSION)) {
                    return "orange";
                } else {
                    return "white";
                }
            })
            .attr("stroke-width", d => {
                if (d.labels.includes(LABEL_OUTDATEDVERSION)) {
                    return 3;
                } else {
                    return 1.5;
                }
            })
            .attr("stroke-opacity", 0)
            .attr("fill-opacity", 0);

        nodeEnter.filter(d => d.labels.includes(LABEL_SERVICE) || d.labels.includes(LABEL_ENDPOINT))
            .attr("d", d3.symbol()
                .size(d => {
                    if(d.labels.includes(LABEL_SERVICE)) {
                        return SIZE_SERVIVE;
                    } else if(d.labels.includes(LABEL_ENDPOINT)) {
                        return SIZE_ENDPOINT;
                    }
                })
                .type((d, i) => {
                    if (d.labels.includes(LABEL_SERVICE)) {
                        return SYMBOL_SERVIVE;
                    } else if(d.labels.includes(LABEL_ENDPOINT)){
                        return SYMBOL_ENDPOINT;
                    }
                })
            );

        nodeEnter.filter(d => d.labels.includes(LABEL_QUEUE))
            .attr("d", d3.symbol()
                .size(d => {
                    if(d.labels.includes(LABEL_QUEUE)) {
                        return SIZE_QUEUE;
                    }
                })
                .type((d, i) => {
                    if (d.labels.includes(LABEL_QUEUE)) {
                        return SYMBOL_QUEUE;
                    }
                }));

        nodeEnter.append("title")
            .text(d => {
                if (d.labels.includes(LABEL_SERVICE)) {
                    return d.appId;
                } else if (d.labels.includes(LABEL_ENDPOINT)) {
                    return d.endpointId;
                } else if (d.labels.includes(LABEL_QUEUE)) {
                    return d.queueId;
                }
            });

        nodeEnter.transition(td)
            .attr("stroke-opacity", 1)
            .attr("fill-opacity", 1);


        node = nodeEnter.merge(node);

        node.on("mouseover", mouseover)
            .on("mouseout", mouseout)
            .on("click", clicked)
            .call(d3.drag()
                .on("start", dragstarted)
                .on("drag", dragged)
                .on("end", dragended));

        // JOIN data and event listeners with old nodelabels
        nodelabel = nodelabel.data(data.nodes, function(d) { return d.id;});

        // EXIT old nodelabels
        nodelabel.exit().remove();

        // UPDATE old nodelabels
        nodelabel.selectAll("rect").remove();
        nodelabel.selectAll("text").remove();

        nodelabel.filter(d =>  d.labels.includes(LABEL_SERVICE))
            .append("text")
            .attr("class", "number-of-instances")
            .attr("fill-opacity", 0.3)
            .attr("alignment-baseline", "central")
            .style("font-size", 28)
            .style("fill", "#000000")
            .text(d => d.number);

        nodelabel.append("rect")
            .attr("class", "tag")
            .attr("fill", "#dddddd")
            .attr("fill-opacity", 0.5)
            .attr("rx", 8)
            .attr("ry", 8);

        nodelabel.append("text")
            .attr("class", "tag")
            .attr("dx", 0)
            .attr("dy", d => {
                if (d.labels.includes(LABEL_SERVICE) || d.labels.includes(LABEL_QUEUE)) {
                    return 53;
                } else if (d.labels.includes(LABEL_ENDPOINT)) {
                    return 39;
                }
            })
            .attr("fill-opacity", 1)
            .text(d => {
                if (d.labels.includes(LABEL_SERVICE)) {
                    return d.appName + ":" + d.version;
                } else if (d.labels.includes(LABEL_ENDPOINT)) {
                    return "[" + d.method + "] " + d.path;
                } else if (d.labels.includes(LABEL_QUEUE)) {
                    return d.queueName;
                }
            });

        nodelabel.selectAll("rect.tag")
            .attr("width", function() {
                return (d3.select(this.parentNode).select("text.tag").node().getBBox().width + 8);
            })
            .attr("height", "16px")
            .attr("x", function() {
                return (d3.select(this.parentNode).select("text.tag").node().getBBox().width + 8) / -2;
            }).attr("y", d => {
            if (d.labels.includes(LABEL_SERVICE) || d.labels.includes(LABEL_QUEUE)) {
                return 40;
            } else if (d.labels.includes(LABEL_ENDPOINT)) {
                return 28;
            }
        });

        let oldNullNodelabel = nodelabel.filter(d => {
            return d.labels.includes(LABEL_NULLSERVICE) || d.labels.includes(LABEL_NULLENDPOINT);
        });

        oldNullNodelabel.append("rect")
            .attr("class", "tag null-tag")
            .attr("fill", "#dddddd")
            .attr("fill-opacity", 0.5)
            .attr("rx", 8)
            .attr("ry", 8);

        oldNullNodelabel.append("text")
            .attr("class", "tag null-tag")
            .attr("dx", 0)
            .attr("dy", function (d) {
                let texts = $(this.parentNode).find("text.tag");
                let position = texts.length - 1;
                if (d.labels.includes(LABEL_SERVICE)) {
                    return 52 + position * 20;
                } else if (d.labels.includes(LABEL_ENDPOINT)) {
                    return 39 + position * 20;
                }
            })
            .attr("fill-opacity", 1)
            .style("fill", "#ce0000")
            .text("<<Null>>");

        oldNullNodelabel.selectAll("rect.null-tag")
            .attr("width", function() {
                let text = d3.select(this.parentNode).select("text.null-tag").node();
                return (text.getBBox().width + 8);
            })
            .attr("height", "16px")
            .attr("x", function() {
                let text = d3.select(this.parentNode).select("text.null-tag").node();
                return (text.getBBox().width + 8) / -2;
            })
            .attr("y", function (d) {
                let texts = $(this.parentNode).find("text.tag");
                let position;
                for (position = 0; position < texts.length; position++) {
                    if (texts[position].textContent === "<<Null>>") {
                        break;
                    }
                }
                if (d.labels.includes(LABEL_SERVICE)) {
                    return 40 + position * 20;
                } else if (d.labels.includes(LABEL_ENDPOINT)) {
                    return 27 + position * 20;
                }
            });

        // ENTER new nodelabels
        let nodelabelEnter = nodelabel.enter().append("g");

        let serviceNodesNum = nodelabelEnter.filter(d => {
            return d.labels.includes(LABEL_SERVICE);
        });

        serviceNodesNum.append("text")
            .attr("class", "number-of-instances")
            .attr("fill-opacity", 0)
            .attr("alignment-baseline", "central")
            .style("font-size", 28)
            .style("fill", "#000000")
            .text(d => {
                if (d.labels.includes(LABEL_SERVICE)) {
                    return d.number;
                }
            });

        nodelabelEnter.append("rect")
            .attr("class", "tag")
            .attr("fill", "#dddddd")
            .attr("fill-opacity", 0)
            .attr("rx", 8)
            .attr("ry", 8);

        nodelabelEnter.append("text")
            .attr("class", "tag")
            .attr("dx", 0)
            .attr("dy", d => {
                if (d.labels.includes(LABEL_SERVICE) || d.labels.includes(LABEL_QUEUE)) {
                    return 53;
                } else if (d.labels.includes(LABEL_ENDPOINT)) {
                    return 39;
                }
            })
            .attr("fill-opacity", 0)
            .text(d => {
                if (d.labels.includes(LABEL_SERVICE)) {
                    return d.appName + ":" + d.version;
                } else if (d.labels.includes(LABEL_ENDPOINT)) {
                    return "[" + d.method + "] " + d.path;
                } else if (d.labels.includes(LABEL_QUEUE)) {
                    return d.queueName;
                }
            });

        nodelabelEnter.selectAll("rect.tag")
            .attr("width", function() {
                return (d3.select(this.parentNode).select("text.tag").node().getBBox().width + 8);
            })
            .attr("height", "16px")
            .attr("x", function() {
                return (d3.select(this.parentNode).select("text.tag").node().getBBox().width + 8) / -2;
            }).attr("y", d => {
            if (d.labels.includes(LABEL_SERVICE) || d.labels.includes(LABEL_QUEUE)) {
                return 40;
            } else if (d.labels.includes(LABEL_ENDPOINT)) {
                return 28;
            }
        });

        let nullNodelabel = nodelabelEnter.filter(d => {
            return d.labels.includes(LABEL_NULLSERVICE) || d.labels.includes(LABEL_NULLENDPOINT);
        });

        nullNodelabel.append("rect")
            .attr("class", "tag null-tag")
            .attr("fill", "#dddddd")
            .attr("fill-opacity", 0)
            .attr("rx", 8)
            .attr("ry", 8);

        nullNodelabel.append("text")
            .attr("class", "tag null-tag")
            .attr("dx", 0)
            .attr("dy", function (d) {
                let texts = $(this.parentNode).find("text.tag");
                let position = texts.length - 1;
                if (d.labels.includes(LABEL_SERVICE)) {
                    return 52 + position * 20;
                } else if (d.labels.includes(LABEL_ENDPOINT)) {
                    return 39 + position * 20;
                }
            })
            .attr("fill-opacity", 0)
            .style("fill", "#ce0000")
            .text("<<Null>>");

        nullNodelabel.selectAll("rect.null-tag")
            .attr("width", function() {
                let text = d3.select(this.parentNode).select("text.null-tag").node();
                return (text.getBBox().width + 8);
            })
            .attr("height", "16px")
            .attr("x", function() {
                let text = d3.select(this.parentNode).select("text.null-tag").node();
                return (text.getBBox().width + 8) / -2;
            })
            .attr("y", function (d) {
                let texts = $(this.parentNode).find("text.tag");
                let position;
                for (position = 0; position < texts.length; position++) {
                    if (texts[position].textContent === "<<Null>>") {
                        break;
                    }
                }
                if (d.labels.includes(LABEL_SERVICE)) {
                    return 40 + position * 20;
                } else if (d.labels.includes(LABEL_ENDPOINT)) {
                    return 27 + position * 20;
                }
            });

        nodelabelEnter.style("pointer-events", "none");

        nodelabelEnter.selectAll("text.number-of-instances")
            .transition(td)
            .attr("fill-opacity", 0.3);

        nodelabelEnter.selectAll("rect.tag")
            .transition(td)
            .attr("fill-opacity", 0.5);

        nodelabelEnter.selectAll("text.tag")
            .transition(td)
            .attr("fill-opacity", 1);

        nodelabel = nodelabelEnter.merge(nodelabel);

        if (enterOrExitEvent) {
            simulation.alpha(1);
        }
        simulation.restart();
        enterOrExitEvent = false;

    }

    function ticked() {
        link.selectAll("line")
            .attr("x1", d => { return d.source.x; })
            .attr("y1", d => { return d.source.y; })
            .attr("x2", d => { return d.target.x; })
            .attr("y2", d => { return d.target.y; });

        link.selectAll("text")
            .attr("x", d => { return (d.source.x + d.target.x) / 2})
            .attr("y", d => { return (d.source.y + d.target.y) / 2 -3})
            .attr("transform", d => {
                return "rotate(" + culDegrees(d.source.x, d.source.y, d.target.x, d.target.y) + " "
                    + (d.source.x + d.target.x) / 2 + " " + (d.source.y + d.target.y) / 2 +  ")";
            });

        node.attr("transform", d => {
            return "translate(" + d.x + "," + d.y + ")";
        });

        nodelabel.attr("transform", d => { return "translate(" + d.x + "," + d.y + ")"; });

    }

    function culDegrees(x1, y1, x2, y2) {
        return Math.atan2(y2 - y1, x2 - x1)*180/Math.PI;
    }

    function clicked(d) {
        let scale = 1.5;
        let translate = [graphWidth / 2 - scale * d.x, graphHeight / 2 - scale * d.y];
        let transform = d3.zoomIdentity
            .translate(translate[0], translate[1])
            .scale(scale);

        svg.transition()
            .duration(600)
            .call(zoom.transform, transform);

        initNodeCard(d);
    }

    function dragstarted(d) {
        if (!d3.event.active) simulation.alphaTarget(0.3).restart();
        d.fx = d.x;
        d.fy = d.y;
    }

    function dragged(d) {
        d.fx = d3.event.x;
        d.fy = d3.event.y;
    }

    function dragended(d) {
        d.fx = null;
        d.fy = null;
    }

    function mouseover(d, i) {
        if (d.labels.includes(LABEL_SERVICE) || d.labels.includes(LABEL_ENDPOINT)) {
            d3.select(this).transition().duration(100)
                .attr("d", d3.symbol()
                    .size(d => {
                        if(d.labels.includes(LABEL_SERVICE)) {
                            return SIZE_SERVIVE * NODE_SCALE;
                        } else if(d.labels.includes(LABEL_ENDPOINT)) {
                            return SIZE_ENDPOINT * NODE_SCALE;
                        }
                    })
                    .type((d, i) => {
                        if (d.labels.includes(LABEL_SERVICE)) {
                            return SYMBOL_SERVIVE;
                        } else if(d.labels.includes(LABEL_ENDPOINT)){
                            return SYMBOL_ENDPOINT;
                        }
                    })
                )
                .attr("fill-opacity", 0.8);
        } else if (d.labels.includes(LABEL_QUEUE)) {
            d3.select(this).transition().duration(100)
                .attr("d", d3.symbol()
                    .size(d => {
                        if(d.labels.includes(LABEL_QUEUE)) {
                            return SIZE_QUEUE * NODE_SCALE;
                        }
                    })
                    .type((d, i) => {
                        if (d.labels.includes(LABEL_QUEUE)) {
                            return SYMBOL_QUEUE;
                        }
                    }))
                .attr("fill-opacity", 0.8);
        }

    }

    function mouseout(d, i) {
        if (d.labels.includes(LABEL_SERVICE) || d.labels.includes(LABEL_ENDPOINT)) {
            d3.select(this).transition().duration(100)
                .attr("d", d3.symbol()
                    .size(d => {
                        if(d.labels.includes(LABEL_SERVICE)) {
                            return SIZE_SERVIVE;
                        } else if(d.labels.includes(LABEL_ENDPOINT)) {
                            return SIZE_ENDPOINT;
                        }
                    })
                    .type((d, i) => {
                        if (d.labels.includes(LABEL_SERVICE)) {
                            return SYMBOL_SERVIVE;
                        } else if(d.labels.includes(LABEL_ENDPOINT)){
                            return SYMBOL_ENDPOINT;
                        }
                    })
                )
                .attr("fill-opacity", 1);
        } else if (d.labels.includes(LABEL_QUEUE)) {
            d3.select(this).transition().duration(100)
                .attr("d", d3.symbol()
                    .size(d => {
                        if(d.labels.includes(LABEL_QUEUE)) {
                            return SIZE_QUEUE;
                        }
                    })
                    .type((d, i) => {
                        if (d.labels.includes(LABEL_QUEUE)) {
                            return SYMBOL_QUEUE;
                        }
                    }))
                .attr("fill-opacity", 1);
        }
    }

    function getNodeById(id) {
        data.nodes.forEach((node => {
            if (node.id === id) {
                return node;
            }
        }));
    }
}

function initNodeCard(d) {
	let card = document.getElementById("node-card");
	let cardHeader = card.getElementsByClassName("card-header")[0];
	let cardClose = cardHeader.getElementsByClassName("close")[0];
	let cardHeaderTitle = cardHeader.getElementsByClassName("card-title")[0];
	let nodeInfoBody = document.getElementById("node-infomation").getElementsByClassName("card-body")[0];
	let nodeInfoTitle = nodeInfoBody.getElementsByClassName("card-title")[0];
	let nodeMonitorBody = document.getElementById("node-monitor").getElementsByClassName("card-body")[0];
	let nodeMonitorTitle = nodeMonitorBody.getElementsByClassName("card-title")[0];

	cardClose.addEventListener("click", function() {
		cardHeader.classList.remove("show");
		nodeInfoBody.classList.remove("show");
		nodeMonitorBody.classList.remove("show");
		card.classList.remove("show");
	});
	
	// Card header
	if (d.labels.includes(LABEL_ENDPOINT)) {
		cardHeaderTitle.innerHTML = "[" + d.method + "] " + d.path;
	} else if (d.labels.includes(LABEL_SERVICE)) {
		cardHeaderTitle.innerHTML = d.appName;
	}
	
	// Node info
	if (d.labels.includes(LABEL_ENDPOINT)) {
		nodeInfoTitle.innerHTML = "";
	} else if (d.labels.includes(LABEL_SERVICE)) {
		nodeInfoTitle.innerHTML = d.version;
	}
	
	//nodeMonitorTitle.innerHTML = d.group;

	if (!card.classList.contains("show")) {
		cardHeader.classList.add("show");
		nodeInfoBody.classList.add("show");
		nodeMonitorBody.classList.add("show");
		card.classList.add("show");
	}

	fetch(d.url + "/health")
	.then(response => response.json())
	.then(json => {
		/*
		var node = document.createElement("pre");
		node.id = "health-json"
		nodeMonitorBody.appendChild(node);
		*/
		$("#health-json").empty();
		$("#health-json").jsonViewer(json, {collapsed: true, withQuotes: false});
	});

	fetch(d.url + "/metrics")
	.then(response => response.json())
	.then(json => {
		$("#metrics-json").empty();
		$("#metrics-json").jsonViewer(json, {collapsed: true, withQuotes: false});
	});
}