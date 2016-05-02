# coding=utf-8
""" Implements the topology for SOL optimization

"""
from os.path import sep

import networkx as nx
from networkx.readwrite import graphml

_HAS_MBOX = 'hasMbox'
_SWITCH = 'switch'
_SERVICES = 'services'
_RESOURCES = 'resources'

# noinspection PyClassicStyleClass
cdef class Topology:
    """
    Class that stores the topology graph and provides helper functions
    (e.g., middlebox manipulation)

    """

    def __init__(self, str name, graph=None):
        """ Create a new empty topology

        :param name: The topology name
        :param graph: Either a
            #) :py:mod:`networkx` graph that represents the topology
            #) filename of a graphml file to load the graph from
            #) None, in which case an empty directed graph is created

        """
        self.name = name
        if graph is not None:
            if isinstance(graph, str):
                self.load_graph(graph)
            else:
                self._graph = graph
        else:
            self._graph = nx.DiGraph()
        self._process_graph()

    def _process_graph(self):
        """
        Initializes all the nodes to switches and sets resource dictionaries.
        """
        for n in self.nodes():
            self.add_service_type(n, _SWITCH)
        for n in self.nodes():
            if _RESOURCES not in self._graph.node[n]:
                self._graph.node[n][_RESOURCES] = {}
        for u, v in self.links():
            if _RESOURCES not in self._graph.edge[u][v]:
                self._graph.edge[u][v][_RESOURCES] = {}

    def num_nodes(self, str service=None):
        """ Returns the number of nodes in this topology

        :param service: only count nodes that provide a particular service (
            e.g., 'switch', 'ids', 'fw', etc.)
        """
        if service is None:
            return self._graph.number_of_nodes()
        else:
            return len([n for n in self._graph.nodes_iter()
                        if 'services' in self._graph.node[n] and
                        service in self._graph.node[n]['services']])

    def get_graph(self):
        """ Return the topology graph

        :return: :py:mod:`networkx` topology directed graph
        """
        return self._graph

    def set_graph(self, graph):
        """ Set the graph

        :param graph: :py:mod:`networkx` directed graph
        """
        self._graph = graph

    def write_graph(self, str dir_name, str fname=None):
        """
        Writes out the graph in GraphML format

        :param dir_name: directory to write to
        :param fname: file name to use. If None, topology name with a
            '.graphml' suffix is used
        """
        n = self.name + '.graphml' if fname is None else fname
        graphml.write_graphml(self._graph, dir_name + sep + n)

    def load_graph(self, str fname):
        """ Loads the topology graph from a file in GraphML format

        :param fname: the name of the file to read from
        """
        self._graph = graphml.read_graphml(fname, int).to_directed()

    def get_service_types(self, node):
        """
        Returns the list of services a particular node provides

        :param node: the node id of interest
        :return: a list of available services at this node (e.g., 'switch',
            'ids')
        """
        return self._graph.node[node][_SERVICES].split(';')

    def set_service_types(self, node, service_types):
        """
        Set the service types for this node

        :param node: the node id of interest
        :param service_types: a list of strings denoting the services
        :type service_types: list
        """
        if isinstance(service_types, str):
            self._graph.node[node][_SERVICES] = service_types
        else:
            self._graph.node[node][_SERVICES] = ';'.join(service_types)

    def add_service_type(self, node, service_type):
        """
        Add a single service type to the given node

        :param node: the node id of interest
        :param service_type: the service to add (e.g., 'switch', 'ids')
        :type service_type: str
        """
        if _SERVICES in self._graph.node[node]:
            types = set(self._graph.node[node][_SERVICES].split(';'))
            types.add(service_type)
        else:
            types = [service_type]
        self._graph.node[node][_SERVICES] = ';'.join(types)

    cpdef nodes(self, data=False):
        """
        :param data: whether to return the attributes associated with the node
        :return: Iterator over topology nodes as tuples of the form (nodeID, nodeData)
        """
        return self._graph.nodes_iter(data=data)

    cpdef edges(self, data=False):
        """
        :param data: whether to return the attributes associated with the edge
        :return: Iterator over topology edge tuples (nodeID1, nodeID2, edgeData)
        """
        return self._graph.edges_iter(data=data)

    links = edges  # Method alias here

    def set_resource(self, node_or_link, str resource, double capacity):
        """
        Set the given resources capacity on a node (or link)
        :param node_or_link: node (or link) for which resource capcity is being
            set
        :param resource: name of the resource
        :param capacity: resource capacity
        """
        if isinstance(node_or_link, tuple):
            assert len(node_or_link) == 2
            self._graph.edge[node_or_link[0]][node_or_link[1]][_RESOURCES][
                resource] = capacity
        else:
            self._graph.node[node_or_link][_RESOURCES][resource] = capacity

    cpdef dict get_resources(self, node_or_link):
        """
        Returns the resources (and their capacities) associated with given
        node or link
        :param node_or_link:
        :return:
        """
        if isinstance(node_or_link, tuple):
            assert len(node_or_link) == 2
            if _RESOURCES in self._graph.edge[node_or_link[0]][node_or_link[1]]:
                return self._graph.edge[node_or_link[0]][node_or_link[1]][
                    _RESOURCES]
            else:
                return {}
        else:
            if _RESOURCES in self._graph.node[node_or_link]:
                return self._graph.node[node_or_link][_RESOURCES]
            else:
                return {}

    def __repr__(self):
        return "{}(name={})".format(self.__class__, self.name)

    def has_middlebox(self, node):
        """
        Check if the given node has a middlebox attached to it

        :param node: node ID to check
        :return: True or False
        """
        try:
            return self._graph.node[node][_HAS_MBOX]
        except KeyError:
            return False

    has_mbox = has_middlebox  # Method alias

    def set_middlebox(self, node, val=True):
        """
        Indicate whether a middlebox is attached to a given node

        :param node: node ID
        :param val: True or False
        """
        self._graph.node[node][_HAS_MBOX] = val

    set_mbox = set_middlebox  # Method alias
