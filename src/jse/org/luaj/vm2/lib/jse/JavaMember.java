/*******************************************************************************
* Copyright (c) 2011 Luaj.org. All rights reserved.
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

import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.CoerceLuaToJava.Coercion;

/**
 * Java method or constructor.
 * <p>
 * Primarily handles argument coercion for parameter lists including scoring of compatibility and
 * java varargs handling.
 * <p>
 * This class is not used directly.
 * It is an abstract base class for {@link JavaConstructor} and {@link JavaMethod}.
 * @see JavaConstructor
 * @see JavaMethod
 * @see CoerceJavaToLua
 * @see CoerceLuaToJava
 */
abstract
class JavaMember extends VarArgFunction {

	static final int METHOD_MODIFIERS_VARARGS = 0x80;
    static final int SCORE_VARARGS = 0x20;

	final Coercion[] fixedargs;
	final Coercion varargs;

	protected JavaMember(Class[] params, int modifiers) {
		boolean isvarargs = ((modifiers & METHOD_MODIFIERS_VARARGS) != 0);
		fixedargs = new CoerceLuaToJava.Coercion[isvarargs? params.length-1: params.length];
		for ( int i=0; i<fixedargs.length; i++ )
			fixedargs[i] = CoerceLuaToJava.getCoercion( params[i] );
		varargs = isvarargs? CoerceLuaToJava.getCoercion( params[params.length-1].getComponentType() ): null;
	}

    int score(Varargs args) {
        int n = args.narg();
        /*
         * Normal methods must have exactly the expected number of arguments.
         */
        if (varargs == null && n != fixedargs.length)
            return CoerceLuaToJava.SCORE_UNCOERCIBLE;
        /*
         * A varargs method must receive all fixed arguments.
         */
        if (varargs != null && n < fixedargs.length)
            return CoerceLuaToJava.SCORE_UNCOERCIBLE;

        int score = 0;

        for (int i = 0; i < fixedargs.length; i++) {
            int s = fixedargs[i].score(args.arg(i + 1));
            if (s >= CoerceLuaToJava.SCORE_UNCOERCIBLE)
                return CoerceLuaToJava.SCORE_UNCOERCIBLE;
            score += s;
        }

        if (varargs != null) {
            /* Penalizes varargs compared to normal methods
             * both should be preferred when applicable.
             */
            score += SCORE_VARARGS;

            for (int i = fixedargs.length; i < n; i++) {
                int s = varargs.score(args.arg(i + 1));
                if (s >= CoerceLuaToJava.SCORE_UNCOERCIBLE)
                    return CoerceLuaToJava.SCORE_UNCOERCIBLE;
                score += s;
            }
        }

        return score;
    }

	protected Object[] convertArgs(Varargs args) {
		Object[] a;
		if ( varargs == null ) {
			a = new Object[fixedargs.length];
			for ( int i=0; i<a.length; i++ )
				a[i] = fixedargs[i].coerce( args.arg(i+1) );
		} else {
			// should be the fixed arguments, followed by an array with the varargs
			a = new Object[fixedargs.length+1];
			int nvar = Math.max(0, args.narg()-fixedargs.length);
			Object[] vararray = new Object[nvar];
			for ( int i=0; i<fixedargs.length; i++ )
				a[i] = fixedargs[i].coerce( args.arg(i+1) );
			a[a.length-1] = vararray;
			for ( int i=0; i<nvar; i++ )
				vararray[i] = varargs.coerce( args.arg(fixedargs.length+i+1) );
		}
		return a;
	}
}
