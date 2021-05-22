package de.upb.swt.soot.core.jimple.javabytecode.stmt;

/*-
 * #%L
 * Soot - a J*va Optimization Framework
 * %%
 * Copyright (C) 2019-2020 Markus Schmidt, Thomas Johannesmeyer
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */

import de.upb.swt.soot.core.jimple.Jimple;
import de.upb.swt.soot.core.jimple.basic.*;
import de.upb.swt.soot.core.jimple.common.constant.IntConstant;
import de.upb.swt.soot.core.jimple.common.stmt.BranchingStmt;
import de.upb.swt.soot.core.jimple.common.stmt.Stmt;
import de.upb.swt.soot.core.jimple.visitor.StmtVisitor;
import de.upb.swt.soot.core.jimple.visitor.Visitor;
import de.upb.swt.soot.core.model.Body;
import de.upb.swt.soot.core.util.Copyable;
import de.upb.swt.soot.core.util.printer.StmtPrinter;
import java.util.*;
import javax.annotation.Nonnull;

/*
 * Switch Statements (combining LookupSwitch/TableSwitch)
 * @author Markus Schmidt
 */
public class JSwitchStmt extends BranchingStmt implements Copyable {

  private final ValueBox keyBox;
  private List<IntConstant> values;
  private final boolean isTableSwitch;

  private JSwitchStmt(
      boolean isTableSwitch, @Nonnull StmtPositionInfo positionInfo, @Nonnull ValueBox keyBox) {
    super(positionInfo);
    this.isTableSwitch = isTableSwitch;
    this.keyBox = keyBox;
  }

  public JSwitchStmt(
      @Nonnull Value key, int lowIndex, int highIndex, @Nonnull StmtPositionInfo positionInfo) {
    this(Jimple.newImmediateBox(key), lowIndex, highIndex, positionInfo);
  }

  public JSwitchStmt(
      @Nonnull ValueBox keyBox,
      int lowIndex,
      int highIndex,
      @Nonnull StmtPositionInfo positionInfo) {
    this(true, positionInfo, keyBox);

    if (lowIndex > highIndex) {
      throw new RuntimeException(
          "Error creating switch: lowIndex("
              + lowIndex
              + ") can't be greater than highIndex("
              + highIndex
              + ").");
    }

    values = new ImmutableSequenceList(lowIndex, highIndex);
  }

  /** Constructs a new JSwitchStmt. lookupValues should be a list of IntConst s. */
  public JSwitchStmt(
      @Nonnull ValueBox keyBox,
      @Nonnull List<IntConstant> lookupValues,
      @Nonnull StmtPositionInfo positionInfo) {
    this(false, positionInfo, keyBox);
    values = Collections.unmodifiableList(new ArrayList<>(lookupValues));
  }

  public JSwitchStmt(
      @Nonnull Value key,
      @Nonnull List<IntConstant> lookupValues,
      @Nonnull StmtPositionInfo positionInfo) {
    this(Jimple.newImmediateBox(key), lookupValues, positionInfo);
  }

  public boolean isTableSwitch() {
    return isTableSwitch;
  }

  @Nonnull
  public Optional<Stmt> getDefaultTarget(Body body) {
    return Optional.ofNullable(body.getBranchTargetsOf(this).get(values.size()));
  }

  public Value getKey() {
    return keyBox.getValue();
  }

  public final ValueBox getKeyBox() {
    return keyBox;
  }

  @Override
  @Nonnull
  public List<Value> getUses() {
    final List<Value> uses = getKey().getUses();
    List<Value> list = new ArrayList<>(uses.size() + 1);
    list.addAll(uses);
    list.add(getKey());
    return list;
  }

  @Override
  public boolean fallsThrough() {
    return false;
  }

  @Override
  public boolean branches() {
    return true;
  }

  @Override
  public void accept(@Nonnull Visitor sw) {
    ((StmtVisitor) sw).caseSwitchStmt(this);
  }

  /** Amount of labels +1 for default handler */
  public int getValueCount() {
    return values.size() + 1;
  }

  public int getValue(int index) {
    return values.get(index).getValue();
  }

  @Nonnull
  public List<IntConstant> getValues() {
    return Collections.unmodifiableList(values);
  }

  @Override
  @Nonnull
  public List<Stmt> getTargetStmts(Body body) {
    return body.getBranchTargetsOf(this);
  }

  @Override
  public int getSuccessorCount() {
    return getValueCount();
  }

  @Override
  public boolean equivTo(@Nonnull Object o, @Nonnull JimpleComparator comparator) {
    return comparator.caseSwitchStmt(this, o);
  }

  @Override
  public int equivHashCode() {
    return Objects.hash(getValues());
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();

    sb.append(Jimple.SWITCH)
        .append('(')
        .append(getKey())
        .append(')')
        .append(' ')
        .append('{')
        .append(" ");

    for (IntConstant value : values) {
      sb.append("    ").append(Jimple.CASE).append(' ').append(value).append(": ");
    }

    sb.append("    ").append(Jimple.DEFAULT).append(": ");
    sb.append(' ').append('}');

    return sb.toString();
  }

  @Override
  public void toString(@Nonnull StmtPrinter stmtPrinter) {
    stmtPrinter.literal(Jimple.SWITCH);
    stmtPrinter.literal("(");
    getKey().toString(stmtPrinter);
    stmtPrinter.literal(")");
    stmtPrinter.newline();
    stmtPrinter.incIndent();
    stmtPrinter.handleIndent();
    stmtPrinter.literal("{");
    stmtPrinter.newline();

    final Iterable<Stmt> targets = stmtPrinter.getBody().getBranchTargetsOf(this);
    Iterator<Stmt> targetIt = targets.iterator();
    for (IntConstant value : values) {
      stmtPrinter.handleIndent();
      stmtPrinter.literal(Jimple.CASE);
      stmtPrinter.literal(" ");
      stmtPrinter.constant(value);
      stmtPrinter.literal(": ");
      stmtPrinter.literal(Jimple.GOTO);
      stmtPrinter.literal(" ");
      stmtPrinter.stmtRef(targetIt.next(), true);
      stmtPrinter.literal(";");

      stmtPrinter.newline();
    }
    Stmt defaultTarget = targetIt.next();
    stmtPrinter.handleIndent();
    stmtPrinter.literal(Jimple.DEFAULT);
    stmtPrinter.literal(": ");
    stmtPrinter.literal(Jimple.GOTO);
    stmtPrinter.literal(" ");
    stmtPrinter.stmtRef(defaultTarget, true);
    stmtPrinter.literal(";");

    stmtPrinter.decIndent();
    stmtPrinter.newline();
    stmtPrinter.handleIndent();
    stmtPrinter.literal("}");
  }

  @Nonnull
  public JSwitchStmt withKey(@Nonnull Immediate key) {
    return new JSwitchStmt(key, getValues(), getPositionInfo());
  }

  @Nonnull
  public JSwitchStmt withValues(@Nonnull List<IntConstant> values) {
    return new JSwitchStmt(getKey(), values, getPositionInfo());
  }

  @Nonnull
  public JSwitchStmt withPositionInfo(@Nonnull StmtPositionInfo positionInfo) {
    return new JSwitchStmt(getKey(), getValues(), positionInfo);
  }

  private static class ImmutableSequenceList implements List<IntConstant> {
    private final int from;
    private final int to;

    ImmutableSequenceList(int from, int to) {
      this.from = from;
      this.to = to;
    }

    @Override
    public int size() {
      return to - from + 1;
    }

    @Override
    public boolean isEmpty() {
      return size() == 0;
    }

    @Override
    public boolean contains(Object o) {
      if (o instanceof IntConstant) {
        int value = ((IntConstant) o).getValue();
        return value >= from && value <= to;
      }
      return false;
    }

    @Nonnull
    @Override
    public Iterator<IntConstant> iterator() {
      return listIterator();
    }

    @Nonnull
    @Override
    public Object[] toArray() {
      Object[] intConstants = new IntConstant[from - to];
      // this allows the full range of ints as cases otherwise there can be an overflow..
      int i;
      for (i = from; i < to; i++) {
        intConstants[i] = IntConstant.getInstance(i);
      }
      intConstants[to] = IntConstant.getInstance(to);
      return intConstants;
    }

    @Nonnull
    @Override
    public <T> T[] toArray(@Nonnull T[] ts) {
      T[] intConstants = (T[]) new Object[from - to + 1];

      // this allows the full range of ints as cases otherwise there can be an overflow..
      int i;
      for (i = from; i < to; i++) {
        intConstants[i] = (T) IntConstant.getInstance(i);
      }
      intConstants[to] = (T) IntConstant.getInstance(to);
      return intConstants;
    }

    @Override
    public boolean add(IntConstant constant) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection<?> collection) {
      for (Object o : collection) {
        if (!contains(o)) {
          return false;
        }
      }
      return true;
    }

    @Override
    public boolean addAll(@Nonnull Collection<? extends IntConstant> collection) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(int i, @Nonnull Collection<? extends IntConstant> collection) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(@Nonnull Collection<?> collection) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(@Nonnull Collection<?> collection) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
      throw new UnsupportedOperationException();
    }

    @Override
    public IntConstant get(int i) {
      // do we need to check bounds too?
      return IntConstant.getInstance(i);
    }

    @Override
    public IntConstant set(int i, IntConstant constant) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void add(int i, IntConstant constant) {
      throw new UnsupportedOperationException();
    }

    @Override
    public IntConstant remove(int i) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int indexOf(Object o) {
      if (!contains(o)) {
        return -1;
      }
      return ((IntConstant) o).getValue() - from;
    }

    @Override
    public int lastIndexOf(Object o) {
      return indexOf(o);
    }

    @Nonnull
    @Override
    public ListIterator<IntConstant> listIterator() {
      return listIterator(0);
    }

    @Nonnull
    @Override
    public ListIterator<IntConstant> listIterator(int i) {
      return new ListIterator<IntConstant>() {
        int it = from + i;

        @Override
        public boolean hasNext() {
          return it <= to;
        }

        @Override
        public IntConstant next() {
          return IntConstant.getInstance(it++);
        }

        @Override
        public boolean hasPrevious() {
          return from <= it;
        }

        @Override
        public IntConstant previous() {
          return IntConstant.getInstance(it--);
        }

        @Override
        public int nextIndex() {
          return it + 1;
        }

        @Override
        public int previousIndex() {
          return it - 1;
        }

        @Override
        public void remove() {
          throw new UnsupportedOperationException();
        }

        @Override
        public void set(IntConstant constant) {
          throw new UnsupportedOperationException();
        }

        @Override
        public void add(IntConstant constant) {
          throw new UnsupportedOperationException();
        }
      };
    }

    @Nonnull
    @Override
    public List<IntConstant> subList(int i, int i1) {
      return new ImmutableSequenceList(i, i1);
    }
  }
}
