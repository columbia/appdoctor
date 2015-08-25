package com.andchecker;

public class IntervalTree<T> {
	BalancedTree<T> tree;
	IntervalFetcher<T> fetch;
	public IntervalTree(IntervalFetcher<T> fetch) {
		this.fetch = fetch;
		this.tree = new Treap<T>(fetch);
	}
	public void insert(T obj) {
		tree.put(obj);
	}
	
	public boolean findOverlap(TreeNode<T> node, T obj) {
		if (node == null) return false;
		if (fetch.start(obj) > fetch.end(node.getObj())) return false;
		if (node.getLeft() != null)
			if (findOverlap(node.getLeft(), obj))
				return true;
		if (fetch.overlap(node.getObj(), obj))
			return true;
		if (fetch.end(obj) < fetch.start(node.getObj())) return false;
		if (node.getRight() != null)
			if (findOverlap(node.getRight(), obj))
				return true;
		return false;
	}
	
	public static abstract class IntervalFetcher<P> implements BalancedTree.Compare<P> {
		abstract int start(P obj);
		abstract int end(P obj);
		boolean overlap(P o1, P o2) {
			return start(o1) < end(o2) && end(o1) > start(o2); 
		}
		@Override
		public int compare(P o1, P o2) {
			if (end(o1) < start(o2))
				return -1;
			else if (start(o1) > end(o2))
				return 1;
			else
				return 0;
		}
	}
	
}

