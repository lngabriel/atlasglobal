import { createBrowserRouter } from 'react-router-dom'
import { lazy, Suspense } from 'react'
import { Layout } from '@/components/layout'
import { HomePage } from '@/pages/home'
import { AboutPage } from '@/pages/about'
import { NotFoundPage } from '@/pages/not-found'
import { CountryPage } from '@/pages/country'
import { ExplorePage } from '@/pages/explore'
import { ComparePage } from '@/pages/compare'
import { RankingsPage } from '@/pages/rankings'
import { LoadingState } from '@/components/query-state'

const WorldMapPage = lazy(() => import('@/pages/world-map').then(module => ({ default: module.WorldMapPage })))
const PolicyRatesPage = lazy(() => import('@/pages/policy-rates').then(module => ({ default: module.PolicyRatesPage })))

export const router = createBrowserRouter([{
  element: <Layout />,
  children: [
    { path: '/', element: <HomePage /> },
    { path: '/explorar', element: <ExplorePage /> },
    { path: '/comparar', element: <ComparePage /> },
    { path: '/rankings', element: <RankingsPage /> },
    { path: '/juros', element: <Suspense fallback={<div className="page-shell py-10"><LoadingState label="Carregando taxas de juros…" /></div>}><PolicyRatesPage /></Suspense> },
    { path: '/mapa', element: <Suspense fallback={<div className="page-shell py-10"><LoadingState label="Preparando o mapa…" /></div>}><WorldMapPage /></Suspense> },
    { path: '/paises/:iso3Code', element: <CountryPage /> },
    { path: '/sobre', element: <AboutPage /> },
    { path: '*', element: <NotFoundPage /> },
  ],
}])
