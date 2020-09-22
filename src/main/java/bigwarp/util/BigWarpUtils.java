package bigwarp.util;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import bdv.util.Affine3DHelpers;
import bdv.viewer.Source;
import bdv.viewer.ViewerPanel;
import bdv.viewer.ViewerState;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.RealInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.iterator.RealIntervalIterator;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.util.LinAlgHelpers;

public class BigWarpUtils
{

	/**
	 * Estimate an interval bounding the given interval after transformation
	 * by sampling points along faces.
	 * 
	 * @param transform the transform
	 * @param interval the starting interval
	 * @param number number of points along each dimension to sample face
	 * @return an new bounding interval
	 */
	public static RealInterval intervalBoundingBoxFacesNumber(
			final RealTransform transform,
			final RealInterval interval,
			final int[] number )
	{
		double[] spacing = new double[ number.length ];
		for( int i = 0; i < number.length; i++ )
		{
			spacing[ i ] = number[ i ] / ( interval.realMax( i ) - interval.realMin( i ) );
		}

		return intervalBoundingBoxFacesSpacing( transform, interval, spacing );
	}

	public static RealInterval intervalBoundingBoxFacesSpacing(
			final RealTransform transform,
			final RealInterval interval,
			final double[] spacing )
	{
		List< RealLocalizable > pts = getFaces( interval ).stream().map( x -> sampleFace( x, spacing ))
				.flatMap( Collection::stream )
				.collect( Collectors.toList() );

		return smallestContainingInterval( pts, transform );
	}

	public static FinalRealInterval smallestContainingInterval( List< RealLocalizable > pts , final RealTransform transform )
	{
		int nd = pts.get( 0 ).numDimensions();
		double[] min = new double[ nd ];
		double[] max = new double[ nd ];

		Arrays.fill(min, Double.MAX_VALUE);
		Arrays.fill(max, Double.MIN_VALUE);

		RealPoint pXfm = new RealPoint( nd );
		for( RealLocalizable p : pts )
		{
			transform.apply( p, pXfm );
			for( int d = 0; d < nd; d++ )
			{
				if( pXfm.getDoublePosition( d ) < min[ d ])
				{
					min[ d ]  = pXfm.getDoublePosition( d );
				}

				if( pXfm.getDoublePosition( d ) > max[ d ])
				{
					max[ d ]  = pXfm.getDoublePosition( d );
				}
			}
		}

		return new FinalRealInterval(min, max);
	}

	public static List<RealInterval> getFaces( final RealInterval interval )
	{
		int nd = interval.numDimensions();
		ArrayList< RealInterval > faces = new ArrayList<>();
		for( int d = 0; d < nd; d++ )
		{
			// face at min(d)
			faces.add( intervalHyperSlice( interval, d, interval.realMin( d )));

			// face at max(d)
			faces.add( intervalHyperSlice( interval, d, interval.realMax( d )));
		}
		return faces;
	}

	public static List< RealPoint > sampleFace( RealInterval interval, double[] spacing )
	{
		ArrayList< RealPoint > pts = new ArrayList<>();
		RealIntervalIterator it = new RealIntervalIterator( interval, spacing );

		while( it.hasNext() )
		{
			RealPoint p = new RealPoint( interval.numDimensions() );
			p.setPosition( it );
			pts.add( p );
		}

		return pts;
	}

	public static RealInterval intervalHyperSlice( final RealInterval interval, int dim, double pos )
	{
		int nd = interval.numDimensions();
		double[] min = new double[ nd ];
		double[] max = new double[ nd ];
		for( int d = 0; d < nd; d++ )
		{
			if( d == dim )
			{
				min[ d ] = pos;
				max[ d ] = pos;
			}
			else
			{
				min[ d ] = interval.realMin( d );
				max[ d ] = interval.realMax( d );
			}
		}

		return new FinalRealInterval( min, max );
	}

	/**
	 * Set a "good" initial viewer transform. The viewer transform is chosen
	 * such that for the first source,
	 * <ul>
	 * <li>the XY plane is aligned with the screen plane,
	 * <li>the <em>z = 0</em> slice is shown,
	 * <li>centered and scaled such that the full <em>dim_x</em> by
	 * <em>dim_y</em> is visible.
	 * </ul>
	 * This calls {@link #initTransform(int, int, boolean, ViewerState)}, using the size
	 * of the viewer's display component.
	 *
	 * @param viewer
	 *            the viewer (containing at least one source) to have its
	 *            transform set.
	 */
	public static void initTransform( final ViewerPanel viewer )
	{
		final Dimension dim = viewer.getDisplay().getSize();
		final ViewerState state = viewer.state();
		final AffineTransform3D viewerTransform = initTransform( dim.width, dim.height, false, state );
		viewer.setCurrentViewerTransform( viewerTransform );
	}

	public static void ensurePositiveZ( final AffineTransform3D xfm )
	{
		xfm.set( Math.abs( xfm.get( 2, 2 )), 2, 2 );
	}

	public static void ensurePositiveDeterminant( final AffineTransform3D xfm )
	{
		if( det( xfm ) < 0 )
			flipX( xfm );
	}
	
	public static double det( final AffineTransform3D xfm )
	{
		return LinAlgHelpers.det3x3(
				xfm.get(0, 0), xfm.get(0, 1), xfm.get(0, 2), 
				xfm.get(1, 0), xfm.get(1, 1), xfm.get(1, 2), 
				xfm.get(2, 0), xfm.get(2, 1), xfm.get(2, 2) );
	}

	public static double dotXy( final AffineTransform3D xfm )
	{
		return  xfm.get(0, 0) * xfm.get( 0, 1 ) +
				xfm.get(1, 0) * xfm.get( 1, 1 ) +
				xfm.get(2, 0) * xfm.get( 2, 1 );
	}

	public static void flipX( final AffineTransform3D xfm )
	{
		for( int i = 0; i < 4; i++ )
			xfm.set( -xfm.get(0, i), 0, i );
	}

	public static void permuteXY( final AffineTransform3D xfm )
	{
		double tmp = 0;
		for( int i = 0; i < 4; i++ )
		{
			tmp = xfm.get( 1, i );
			xfm.set( xfm.get(0, i), 1, i );
			xfm.set( tmp, 0, i );
		}
	}

	/**
	 * Get a "good" initial viewer transform for 2d. for 2d. for 2d. for 2d. for 2d. for 2d. for 2d. for 2d. The viewer transform is chosen
	 * such that for the first source,
	 * <ul>
	 * <li>the XY plane is aligned with the screen plane,
	 * <li>the <em>z = 0</em> slice is shown,
	 * <li>centered and scaled such that the full <em>dim_x</em> by
	 * <em>dim_y</em> is visible.
	 * </ul>
	 *
	 * @param viewerWidth
	 *            width of the viewer display
	 * @param viewerHeight
	 *            height of the viewer display
	 * @param state
	 *            the {@link ViewerState} containing at least one source.
	 * @return proposed initial viewer transform.
	 */
	public static AffineTransform3D initTransform( final int viewerWidth, final int viewerHeight, final boolean zoomedIn, final ViewerState state )
	{
		final int cX = viewerWidth / 2;
		final int cY = viewerHeight / 2;

		final Source< ? > source = state.getCurrentSource().getSpimSource();
		final int timepoint = state.getCurrentTimepoint();
		if ( !source.isPresent( timepoint ) )
			return new AffineTransform3D();

		final AffineTransform3D sourceTransform = new AffineTransform3D();
		source.getSourceTransform( timepoint, 0, sourceTransform );

		final Interval sourceInterval = source.getSource( timepoint, 0 );
		final double sX0 = sourceInterval.min( 0 );
		final double sX1 = sourceInterval.max( 0 );
		final double sY0 = sourceInterval.min( 1 );
		final double sY1 = sourceInterval.max( 1 );
		final double sX = ( sX0 + sX1 + 1 ) / 2;
		final double sY = ( sY0 + sY1 + 1 ) / 2;

		final double[][] m = new double[ 3 ][ 4 ];

		// rotation
		final double[] qSource = new double[ 4 ];
		final double[] qViewer = new double[ 4 ];
		Affine3DHelpers.extractApproximateRotationAffine( sourceTransform, qSource, 2 );
		LinAlgHelpers.quaternionInvert( qSource, qViewer );
		LinAlgHelpers.quaternionToR( qViewer, m );

		// translation
		final double[] centerSource = new double[] { sX, sY, 0 };
		final double[] centerGlobal = new double[ 3 ];
		final double[] translation = new double[ 3 ];
		sourceTransform.apply( centerSource, centerGlobal );
		LinAlgHelpers.quaternionApply( qViewer, centerGlobal, translation );
		LinAlgHelpers.scale( translation, -1, translation );
		LinAlgHelpers.setCol( 3, translation, m );

		final AffineTransform3D viewerTransform = new AffineTransform3D();
		viewerTransform.set( m );

		// scale
		final double[] pSource = new double[] { sX1 + 0.5, sY1 + 0.5, 0.0 };
		final double[] pGlobal = new double[ 3 ];
		final double[] pScreen = new double[ 3 ];
		sourceTransform.apply( pSource, pGlobal );
		viewerTransform.apply( pGlobal, pScreen );
		final double scaleX = cX / pScreen[ 0 ];
		final double scaleY = cY / pScreen[ 1 ];
		final double scale;
		if ( zoomedIn )
			scale = Math.max( scaleX, scaleY );
		else
			scale = Math.min( scaleX, scaleY );
		viewerTransform.scale( scale );

		// window center offset
		viewerTransform.set( viewerTransform.get( 0, 3 ) + cX, 0, 3 );
		viewerTransform.set( viewerTransform.get( 1, 3 ) + cY, 1, 3 );
		return viewerTransform;
	}

	/**
	 * Computes the angle of rotation between the two input quaternions,
	 * returning the result in radians.  Assumes the inputs are unit quaternions.
	 * 
	 * @param q1 first quaternion
	 * @param q2 second quaternion
	 * @return the angle in radians
	 */
	public static double quaternionAngle( double[] q1, double[] q2 )
	{
		double dot = 0;
		for( int i = 0; i < 4; i++ )
			dot += ( q1[ i ] * q2[ i ]);

		return Math.acos( 2 * dot * dot  - 1);
	}

	public static void normalize( double[] x )
	{
		double magSqr = 0;
		for( int i = 0; i < x.length; i++ )
			magSqr += (x[ i ] * x[ i ]);

		for( int i = 0; i < x.length; i++ )
			x[ i ] /= magSqr;
	}
}
