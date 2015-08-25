package com.andchecker;

import java.util.Random;

public abstract class BalancedTree<T> {
	abstract void put(T obj);
	Compare<T> compare;
	BalancedTree(Compare<T> compare) {
		this.compare = compare;
	}
	public interface Compare<T> {
		int compare(T o1, T o2);
	}
}

class TreeNode<T> {
	T obj;
	TreeNode<T> left, right;
	TreeNode(T obj) {
		this.obj = obj;
	}
	
	TreeNode<T> getLeft() { return left; }
	TreeNode<T> getRight() { return right; }
	T getObj() { return obj; }
}

class Treap<T> extends BalancedTree<T> {
	Random random;
	TreapNode<T> root;
	Treap(Compare<T> compare) {
		super(compare);
		random = new Random();
		root = null;
	}
	class TreapNode<T> extends TreeNode<T> {
		int prio;
		TreapNode(T obj) {
			super(obj);
			prio = random.nextInt();
		}
	}
	@Override
	void put(T obj) {
		TreapNode<T> node = new TreapNode<T>(obj);
		if (root == null)
			root = node;
		else
			insertNode(root, node);
	}
	
	void insertNode(TreapNode<T> node, TreapNode<T> newNode) {
		
	}
	
	
}