# coding=utf-8
import networkx
import pytest
from hypothesis import settings

from sol.topology.topologynx import Topology
from sol.topology.generators import complete_topology, chain_topology, \
    fat_tree, CORE_LAYER
from sol.utils.const import SWITCH, CORE_LAYER
from hypothesis import given
import hypothesis.strategies as st


@given(st.integers(0, 50))
@settings(max_examples=50)
def test_complete_generators(size):
    """
    Some basic tests for complete topology generators. Ensure we return correct types
    and correct number of nodes.
    """
    topo = complete_topology(size)
    assert isinstance(topo, Topology)
    assert isinstance(topo.get_graph(), networkx.DiGraph)
    assert topo.num_nodes() == size
    assert networkx.is_isomorphic(topo.get_graph().to_undirected(),
                                  networkx.complete_graph(size))


@given(st.integers(0, 50))
@settings(max_examples=50)
def test_chain_generators(size):
    """
    Some basic tests for chain topology generators. Ensure we return correct types
    and correct number of nodes.
    """
    topo = chain_topology(size)
    assert isinstance(topo, Topology)
    assert isinstance(topo.get_graph(), networkx.DiGraph)
    assert networkx.is_isomorphic(topo.get_graph().to_undirected(),
                                  networkx.path_graph(size))


@given(st.integers(0, 20).filter(lambda x: x % 2 == 0))  # only even numbers
@settings(deadline=10000)
def test_fattree_generator(size):
    """
    Some basic test for FatTree generator. Ensure we return correct types
    and correct number of nodes. Additionally, check that core has correct
    number of switches and fatter links
    """
    topo = fat_tree(size)
    assert isinstance(topo, Topology)
    assert isinstance(topo.get_graph(), networkx.DiGraph)
    # correct size
    assert topo.num_nodes() == 5 * (size ** 2) / 4  # \frac{5}{4} \times size^2
    G = topo.get_graph()
    # every node has a layer attribute
    assert all([u'layer' in G.nodes[n] for n in G.nodes()])
    # there are correct number of core switches
    assert len([n for n in G.nodes() if
                G.nodes[n][u'layer'] == CORE_LAYER]) == size ** 2 / 4
    assert all([u'capacitymult' in G.edges[link] for link in G.edges()])


def test_fattree_err_message():
    """ Check error message on uneven kx topology"""
    with pytest.raises(ValueError):
        fat_tree(9)


def test_switch_labels():
    """
    Ensure that switches are labeled appropriately by default
    """
    for topo in [complete_topology(5), chain_topology(5)]:
        for node in topo.nodes():
            assert SWITCH in topo.get_service_types(node)
