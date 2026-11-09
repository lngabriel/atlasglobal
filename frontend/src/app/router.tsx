import { createBrowserRouter } from 'react-router-dom'
import { Layout } from '@/components/layout'
import { HomePage } from '@/pages/home'
import { AboutPage } from '@/pages/about'
import { NotFoundPage } from '@/pages/not-found'
import { CountryPage } from '@/pages/country'
import { ExplorePage } from '@/pages/explore'
import { ComparePage } from '@/pages/compare'

export const router = createBrowserRouter([{
  element: <Layout />,
  children: [
    { path: '/', element: <HomePage /> },
    { path: '/explorar', element: <ExplorePage /> },
    { path: '/comparar', element: <ComparePage /> },
    { path: '/paises/:iso3Code', element: <CountryPage /> },
    { path: '/sobre', element: <AboutPage /> },
    { path: '*', element: <NotFoundPage /> },
  ],
}])
