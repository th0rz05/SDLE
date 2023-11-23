import networkx as nx
import matplotlib.pyplot as plt
import random

def find_edges_for_connected_component(vertices):
    edges_required = []
    median_edges = []

    for v in vertices:
        edges_for_v = []
        num_experiments = 30

        for _ in range(num_experiments):
            G = nx.gnp_random_graph(v, p=0)  # Create an empty graph with v vertices
            edges_added = 0

            while not nx.is_connected(G):
                nodes = list(G.nodes())
                random.shuffle(nodes)
                new_edge = (nodes[0], nodes[1])
                G.add_edge(*new_edge)
                edges_added += 1

            edges_for_v.append(edges_added)

        edges_required.append(edges_for_v)
        median_edges.append(sum(edges_for_v) / num_experiments)

    return edges_required, median_edges

# Generate a range of vertices
num_vertices = range(5, 101, 5)  # Change the range as needed
edges_needed, median_edges = find_edges_for_connected_component(num_vertices)

# Set up better aesthetics for the plots
plt.figure(figsize=(14, 6))

# Boxplot
plt.subplot(1, 2, 1)
plt.boxplot(edges_needed, labels=[str(v) for v in num_vertices], showmeans=True, patch_artist=True)
plt.xlabel('Number of Vertices')
plt.ylabel('Number of Edges Required')
plt.title('Distribution of Edges Required for Single Connected Component')
plt.grid(True)
plt.xticks(fontsize=8)
plt.yticks(fontsize=8)
plt.tight_layout()

# Median line graph
plt.subplot(1, 2, 2)
plt.plot(num_vertices, median_edges, marker='o', linestyle='-', color='skyblue')
plt.xlabel('Number of Vertices')
plt.ylabel('Median Number of Edges')
plt.title('Median Number of Edges by Number of Vertices')
plt.grid(True)
plt.xticks(fontsize=8)
plt.yticks(fontsize=8)
plt.tight_layout()

plt.show()
