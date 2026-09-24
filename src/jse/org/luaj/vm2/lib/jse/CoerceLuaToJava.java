/*******************************************************************************
* Copyright (c) 2009-2011 Luaj.org. All rights reserved.
*
* Permission is hereby granted, free of charge, to any person obtaining a copy
* of this software and associated documentation files (the "Software"), to deal
* in the Software without restriction, including without limitation the rights
* to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the Software is
* furnished to do so, subject to the following conditions:
*
* The above copyright notice and this permission notice shall be included in
* all copies or substantial portions of the Software.
*
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
* IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
* FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
* AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
* LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
* OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
* THE SOFTWARE.
******************************************************************************/
package org.luaj.vm2.lib.jse;

import java.lang.reflect.Array;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * Helper class to coerce values from lua to Java within the luajava library.
 * <p>
 * This class is primarily used by the {@link org.luaj.vm2.lib.jse.LuajavaLib},
 * but can also be used directly when working with Java/lua bindings.
 * <p>
 * To coerce to specific Java values, generally the {@code toType()} methods
 * on {@link LuaValue} may be used:
 * <ul>
 * <li>{@link LuaValue#toboolean()}</li>
 * <li>{@link LuaValue#tobyte()}</li>
 * <li>{@link LuaValue#tochar()}</li>
 * <li>{@link LuaValue#toshort()}</li>
 * <li>{@link LuaValue#toint()}</li>
 * <li>{@link LuaValue#tofloat()}</li>
 * <li>{@link LuaValue#todouble()}</li>
 * <li>{@link LuaValue#tojstring()}</li>
 * <li>{@link LuaValue#touserdata()}</li>
 * <li>{@link LuaValue#touserdata(Class)}</li>
 * </ul>
 * <p>
 * For data in lua tables, the various methods on {@link LuaTable} can be used directly
 * to convert data to something more useful.
 *
 * @see org.luaj.vm2.lib.jse.LuajavaLib
 * @see CoerceJavaToLua
 */
public class CoerceLuaToJava {

	static int SCORE_NULL_VALUE     =    0x10;
	static int SCORE_WRONG_TYPE     =   0x100;
	static int SCORE_UNCOERCIBLE    = 0x10000;
	static int SCORE_OBJECT_FALLBACK = 0x40;
	static int SCORE_INHERITANCE = 0x20;

	static interface Coercion {
		public int score( LuaValue value );
		public Object coerce( LuaValue value );
	};

	/**
	 * Coerce a LuaValue value to a specified java class
	 * @param value LuaValue to coerce
	 * @param clazz Class to coerce into
	 * @return Object of type clazz (or a subclass) with the corresponding value.
	 */
	public static Object coerce(LuaValue value, Class clazz) {
		return getCoercion(clazz).coerce(value);
	}

	static final Map COERCIONS = Collections.synchronizedMap(new HashMap());

	static final class BoolCoercion implements Coercion {
		public String toString() {
			return "BoolCoercion()";
		}
		public int score( LuaValue value ) {
			switch ( value.type() ) {
			case LuaValue.TBOOLEAN:
				return 0;
			}
			return 1;
		}

		public Object coerce(LuaValue value) {
			return value.toboolean()? Boolean.TRUE: Boolean.FALSE;
		}
	}

	static final class NumericCoercion implements Coercion {
		static final int TARGET_TYPE_BYTE = 0;
		static final int TARGET_TYPE_CHAR = 1;
		static final int TARGET_TYPE_SHORT = 2;
		static final int TARGET_TYPE_INT = 3;
		static final int TARGET_TYPE_LONG = 4;
		static final int TARGET_TYPE_FLOAT = 5;
		static final int TARGET_TYPE_DOUBLE = 6;
		static final String[] TYPE_NAMES = { "byte", "char", "short", "int", "long", "float", "double" };
		final int targetType;
		final boolean nullable;
		public String toString() {
			return "NumericCoercion("+TYPE_NAMES[targetType]+")";
		}
		NumericCoercion(int targetType, boolean nullable) {
            this.targetType = targetType;
            this.nullable = nullable;
        }
		public int score(LuaValue value) {
            if (value.isnil()) {
                return nullable ? SCORE_NULL_VALUE : SCORE_UNCOERCIBLE;
            }

            int fromStringPenalty = 0;

            if (value.type() == LuaValue.TSTRING) {
                value = value.tonumber();
                if (value.isnil()) {
                    return SCORE_UNCOERCIBLE;
                }
                fromStringPenalty = 4;
            }

            if (value.isint()) {
                switch (targetType) {
                case TARGET_TYPE_BYTE: {
                    int i = value.toint();
                    return fromStringPenalty +
                            ((i == (byte)i) ? 0 : SCORE_WRONG_TYPE);
                }

                case TARGET_TYPE_CHAR: {
                    int i = value.toint();
                    return fromStringPenalty +
                            ((i == (byte)i) ? 1 :
                             (i == (char)i) ? 0 :
                             SCORE_WRONG_TYPE);
                }

                case TARGET_TYPE_SHORT: {
                    int i = value.toint();
                    return fromStringPenalty +
                            ((i == (byte)i) ? 1 :
                             (i == (short)i) ? 0 :
                             SCORE_WRONG_TYPE);
                }

                case TARGET_TYPE_INT:
                    return fromStringPenalty + 0;

                case TARGET_TYPE_LONG:
                    return fromStringPenalty + 1;

                case TARGET_TYPE_FLOAT:
                    return fromStringPenalty + 2;

                case TARGET_TYPE_DOUBLE:
                    return fromStringPenalty + 3;

                default:
                    return SCORE_WRONG_TYPE;
                }
            } else if (value.isnumber()) {
                switch (targetType) {
                case TARGET_TYPE_BYTE:
                case TARGET_TYPE_CHAR:
                case TARGET_TYPE_SHORT:
                case TARGET_TYPE_INT:
                    return SCORE_WRONG_TYPE;

                case TARGET_TYPE_LONG: {
                    double d = value.todouble();
                    return fromStringPenalty +
                            ((d == (long)d) ? 1 : SCORE_WRONG_TYPE);
                }

                case TARGET_TYPE_FLOAT: {
                    double d = value.todouble();
                    return fromStringPenalty +
                            ((d == (float)d) ? 1 : SCORE_WRONG_TYPE);
                }

                case TARGET_TYPE_DOUBLE:
                    return fromStringPenalty + 0;

                default:
                    return SCORE_WRONG_TYPE;
                }
            } else {
                return SCORE_UNCOERCIBLE;
            }
        }

        public Object coerce(LuaValue value) {
            if (value.isnil() && nullable) {
                return null;
            }

            switch ( targetType ) {
                case TARGET_TYPE_BYTE: return new Byte((byte) value.toint());
                case TARGET_TYPE_CHAR: return new Character((char) value.toint());
                case TARGET_TYPE_SHORT: return new Short((short) value.toint());
                case TARGET_TYPE_INT: return new Integer((int) value.toint());
                case TARGET_TYPE_LONG: return new Long((long) value.todouble());
                case TARGET_TYPE_FLOAT: return new Float((float) value.todouble());
                case TARGET_TYPE_DOUBLE: return new Double((double) value.todouble());
                default: return null;
            }
        }
	}

	static final class StringCoercion implements Coercion {
		public static final int TARGET_TYPE_STRING = 0;
		public static final int TARGET_TYPE_BYTES = 1;
		final int targetType;
		public StringCoercion(int targetType) {
			this.targetType = targetType;
		}
		public String toString() {
			return "StringCoercion("+(targetType==TARGET_TYPE_STRING? "String": "byte[]")+")";
		}
		public int score(LuaValue value) {
			switch ( value.type() ) {
			case LuaValue.TSTRING:
				return value.checkstring().isValidUtf8()?
						(targetType==TARGET_TYPE_STRING? 0: 1):
						(targetType==TARGET_TYPE_BYTES? 0: SCORE_WRONG_TYPE);
			case LuaValue.TNIL:
				return SCORE_NULL_VALUE;
			default:
				return targetType == TARGET_TYPE_STRING? SCORE_WRONG_TYPE: SCORE_UNCOERCIBLE;
			}
		}
		public Object coerce(LuaValue value) {
			if ( value.isnil() )
				return null;
			if ( targetType == TARGET_TYPE_STRING )
				return value.tojstring();
			LuaString s = value.checkstring();
			byte[] b = new byte[s.m_length];
			s.copyInto(0, b, 0, b.length);
			return b;
		}
	}

	static final class ArrayCoercion implements Coercion {
		final Class componentType;
		final Coercion componentCoercion;
		public ArrayCoercion(Class componentType) {
			this.componentType = componentType;
			this.componentCoercion = getCoercion(componentType);
		}
		public String toString() {
			return "ArrayCoercion("+componentType.getName()+")";
		}
		public int score(LuaValue value) {
			switch ( value.type() ) {
			case LuaValue.TTABLE:
				return value.length()==0? 0: componentCoercion.score( value.get(1) );
			case LuaValue.TUSERDATA:
				return inheritanceLevels( componentType, value.touserdata().getClass().getComponentType() );
			case LuaValue.TNIL:
				return SCORE_NULL_VALUE;
			default:
				return SCORE_UNCOERCIBLE;
			}
		}
		public Object coerce(LuaValue value) {
			switch ( value.type() ) {
			case LuaValue.TTABLE: {
				int n = value.length();
				Object a = Array.newInstance(componentType, n);
				for ( int i=0; i<n; i++ )
					Array.set(a, i, componentCoercion.coerce(value.get(i+1)));
				return a;
			}
			case LuaValue.TUSERDATA:
				return value.touserdata();
			case LuaValue.TNIL:
				return null;
			default:
				return null;
			}

		}
	}

	/**
	 * Determine levels of inheritance between a base class and a subclass
	 * @param baseclass base class to look for
	 * @param subclass class from which to start looking
	 * @return number of inheritance levels between subclass and baseclass,
	 * or SCORE_UNCOERCIBLE if not a subclass
	 */
	static final int inheritanceLevels( Class baseclass, Class subclass ) {
		if ( subclass == null )
			return SCORE_UNCOERCIBLE;
		if ( baseclass == subclass )
			return 0;
		int min = Math.min( SCORE_UNCOERCIBLE, inheritanceLevels( baseclass, subclass.getSuperclass() ) + 1 );
		Class[] ifaces = subclass.getInterfaces();
		for ( int i=0; i<ifaces.length; i++ )
			min = Math.min(min, inheritanceLevels(baseclass, ifaces[i]) + 1 );
		return min;
	}

	static final class ObjectCoercion implements Coercion {
		final Class targetType;
		ObjectCoercion(Class targetType) {
			this.targetType = targetType;
		}
		public String toString() {
			return "ObjectCoercion("+targetType.getName()+")";
		}
		public int score(LuaValue value) {
            switch (value.type()) {
            case LuaValue.TNUMBER:
                if (value.isint()) {
                    if (targetType == Integer.class ||
                        targetType == Integer.TYPE) {
                        return 0;
                    }
                    if (targetType == Long.class ||
                        targetType == Long.TYPE) {
                        return 10;
                    }
                    if (targetType == Float.class ||
                        targetType == Float.TYPE) {
                        return 20;
                    }
                    if (targetType == Double.class ||
                        targetType == Double.TYPE) {
                        return 30;
                    }
                    if (targetType == Object.class) {
                        return SCORE_OBJECT_FALLBACK;
                    }
                    return SCORE_INHERITANCE + inheritanceLevels(targetType, Integer.class);
                 } else {
                    if (targetType == Double.class ||
                        targetType == Double.TYPE) {
                        return 0;
                    }
                    if (targetType == Float.class ||
                        targetType == Float.TYPE) {
                        return 10;
                    }
                    if (targetType == Object.class) {
                        return SCORE_OBJECT_FALLBACK;
                    }
                    return SCORE_INHERITANCE + inheritanceLevels(targetType, Double.class);
               }
            case LuaValue.TBOOLEAN:
                if (targetType == Boolean.class ||
                    targetType == Boolean.TYPE) {
                    return 0;
                }

                if (targetType == Object.class) {
                    return SCORE_OBJECT_FALLBACK;
                }

                return inheritanceLevels(targetType, Boolean.class);

            case LuaValue.TSTRING:
                if (targetType == String.class) {
                    return 0;
                }

                if (targetType == Object.class) {
                    return SCORE_OBJECT_FALLBACK;
                }

                return inheritanceLevels(targetType, String.class);

            case LuaValue.TUSERDATA:
                return inheritanceLevels(
                    targetType,
                    value.touserdata().getClass()
                );

            case LuaValue.TNIL:
                return SCORE_NULL_VALUE;

            default:
                if (targetType == Object.class) {
                    return SCORE_OBJECT_FALLBACK;
                }

                return inheritanceLevels(targetType, value.getClass());
            }
        }
		public Object coerce(LuaValue value) {
			switch ( value.type() ) {
			case LuaValue.TNUMBER:
				return value.isint()? (Object)new Integer(value.toint()): (Object)new Double(value.todouble());
			case LuaValue.TBOOLEAN:
				return value.toboolean()? Boolean.TRUE: Boolean.FALSE;
			case LuaValue.TSTRING:
				return value.tojstring();
			case LuaValue.TUSERDATA:
				return value.optuserdata(targetType, null);
			case LuaValue.TNIL:
				return null;
			default:
				return value;
			}
		}
	}

	static {
		Coercion boolCoercion   = new BoolCoercion();
        Coercion byteCoercion = new NumericCoercion(NumericCoercion.TARGET_TYPE_BYTE, false);
        Coercion byteWrapperCoercion = new NumericCoercion(NumericCoercion.TARGET_TYPE_BYTE, true);
        Coercion charCoercion = new NumericCoercion(NumericCoercion.TARGET_TYPE_CHAR, false);
        Coercion charWrapperCoercion = new NumericCoercion(NumericCoercion.TARGET_TYPE_CHAR, true);
        Coercion shortCoercion = new NumericCoercion(NumericCoercion.TARGET_TYPE_SHORT, false);
        Coercion shortWrapperCoercion = new NumericCoercion(NumericCoercion.TARGET_TYPE_SHORT, true);
        Coercion intCoercion = new NumericCoercion(NumericCoercion.TARGET_TYPE_INT, false);
        Coercion intWrapperCoercion = new NumericCoercion(NumericCoercion.TARGET_TYPE_INT, true);
        Coercion longCoercion = new NumericCoercion(NumericCoercion.TARGET_TYPE_LONG, false);
        Coercion longWrapperCoercion = new NumericCoercion(NumericCoercion.TARGET_TYPE_LONG, true);
        Coercion floatCoercion = new NumericCoercion(NumericCoercion.TARGET_TYPE_FLOAT, false);
        Coercion floatWrapperCoercion = new NumericCoercion(NumericCoercion.TARGET_TYPE_FLOAT, true);
        Coercion doubleCoercion = new NumericCoercion(NumericCoercion.TARGET_TYPE_DOUBLE, false);
        Coercion doubleWrapperCoercion = new NumericCoercion(NumericCoercion.TARGET_TYPE_DOUBLE, true);
        Coercion stringCoercion = new StringCoercion(StringCoercion.TARGET_TYPE_STRING);
		Coercion bytesCoercion  = new StringCoercion(StringCoercion.TARGET_TYPE_BYTES);

		COERCIONS.put(Byte.TYPE, byteCoercion);
        COERCIONS.put(Byte.class, byteWrapperCoercion);
        COERCIONS.put(Character.TYPE, charCoercion);
        COERCIONS.put(Character.class, charWrapperCoercion);
        COERCIONS.put(Short.TYPE, shortCoercion);
        COERCIONS.put(Short.class, shortWrapperCoercion);
        COERCIONS.put(Integer.TYPE, intCoercion);
        COERCIONS.put(Integer.class, intWrapperCoercion);
        COERCIONS.put(Long.TYPE, longCoercion);
        COERCIONS.put(Long.class, longWrapperCoercion);
        COERCIONS.put(Float.TYPE, floatCoercion);
        COERCIONS.put(Float.class, floatWrapperCoercion);
        COERCIONS.put(Double.TYPE, doubleCoercion);
        COERCIONS.put(Double.class, doubleWrapperCoercion);
        COERCIONS.put(Boolean.TYPE, boolCoercion);
        COERCIONS.put(Boolean.class, boolCoercion);
        COERCIONS.put(String.class, stringCoercion);
        COERCIONS.put(byte[].class, bytesCoercion);
	}

	static Coercion getCoercion(Class c) {
		Coercion co = (Coercion) COERCIONS.get( c );
		if ( co != null ) {
			return co;
		}
		if ( c.isArray() ) {
			Class typ = c.getComponentType();
			co = new ArrayCoercion(c.getComponentType());
		} else {
			co = new ObjectCoercion(c);
		}
		COERCIONS.put( c, co );
		return co;
	}
}
